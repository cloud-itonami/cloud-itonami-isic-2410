(ns steelworks.steelworksadvisor
  "Steelworks Advisor client -- the *contained intelligence node* for
  the basic-iron-steel actor.

  It normalizes assembly-intake, drafts a per-jurisdiction
  mill-rules evidence checklist, screens heats
  for an unresolved quality-detected defect, drafts the heat-dispatch
  action, and drafts the mill-cert-issuance action.
  CRITICAL: it is a smart-but-untrusted advisor. It returns a
  *proposal* (with a rationale + the fields it cited), never a
  committed record or a real robot dispatch/mill-cert
  issuance. Every output is censored downstream by `steelworks.
  governor` before anything touches the SSoT, and `:actuation/
  dispatch-assembly`/`:actuation/issue-mill-cert`
  proposals NEVER auto-commit at any phase -- see README `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :actuation/dispatch-heat | :actuation/issue-mill-cert | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [steelworks.facts :as facts]
            [steelworks.registry :as registry]
            [steelworks.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the heat, dimensional-tolerance figures or
  jurisdiction. High confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "ブロック記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :heat/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- verify-requirements
  "Per-jurisdiction mill-rules evidence checklist
  draft. `:no-spec?` injects the failure mode we must defend against:
  proposing a checklist for a jurisdiction with NO official spec-basis
  in `steelworks.facts` -- the Steelworks Governor must
  reject this (never invent a jurisdiction's requirements)."
  [db {:keys [subject no-spec?]}]
  (let [a (store/heat db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction a))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "steelworks.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :verification/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :verification/set
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- screen-quality-defect
  "quality-defect screening draft. `:quality-defect-unresolved?` on the
  heat record injects the failure mode: the Steelworks
  Manufacturing Governor must HOLD, un-overridably, on any unresolved
  defect."
  [db {:keys [subject]}]
  (let [a (store/heat db subject)]
    (cond
      (nil? a)
      {:summary "対象ブロック記録が見つかりません" :rationale "no heat record"
       :cites [] :effect :quality-screen/set :value {:heat-id subject :verdict :unknown}
       :stake nil :confidence 0.0}

      (true? (:quality-defect-unresolved? a))
      {:summary    (str (:unit-name a) ": 未解決の品質検査欠陥を検出")
       :rationale  "スクリーニングが未解決の品質検査欠陥を検出。人手確認とホールドが必須。"
       :cites      [:ndt-check]
       :effect     :quality-screen/set
       :value      {:heat-id subject :verdict :unresolved}
       :stake      nil
       :confidence 0.95}

      :else
      {:summary    (str (:unit-name a) ": 未解決の品質検査欠陥なし")
       :rationale  "品質検査欠陥スクリーニング完了。"
       :cites      [:ndt-check]
       :effect     :quality-screen/set
       :value      {:heat-id subject :verdict :resolved}
       :stake      nil
       :confidence 0.9})))

(defn- propose-block-dispatch
  "Draft the actual ASSEMBLY-DISPATCH action -- dispatching a real
  robot fastening/layup/quality action on a process-critical structure.
  ALWAYS `:stake :actuation/dispatch-heat` -- this is a REAL-WORLD
  safety-critical act, never a draft the actor may auto-run. See
  README `Actuation`: no phase ever adds this op to a phase's `:auto`
  set (`steelworks.phase`); the governor also always escalates on
  `:actuation/dispatch-heat`. Two independent layers agree,
  deliberately.

  Additive (superproject part-supplier-linkage ADR, cloud-itonami-
  isic-2410<->cloud-itonami-isic-2813): the request may OPTIONALLY
  carry a `:handoff` (the superproject `:handoff` shared shape,
  ADR-2607177600, reused as-is) naming the downstream consumer this
  dispatched block/heat is destined for. The advisor only echoes the
  caller's own `:handoff` verbatim into `:value` -- it never invents
  one; `steelworks.governor` INDEPENDENTLY re-verifies its required
  fields (when present) before anything commits."
  [db {:keys [subject handoff]}]
  (let [a (store/heat db subject)]
    {:summary    (str subject " 向けブロック実行提案"
                      (when a (str " (block=" (:unit-name a) ")"))
                      (when-let [src (:handoff/source-actor handoff)] (str " supplier-of=" src)))
     :rationale  (if a
                   (str "chemistry-deviation-actual=" (:chemistry-deviation-actual a)
                        " spec=[" (:chemistry-deviation-min a) "," (:chemistry-deviation-max a) "]")
                   "ブロック記録が見つかりません")
     :cites      (if a (cond-> [subject] (:handoff/source-actor handoff) (conj (:handoff/source-actor handoff))) [])
     :effect     :heat/mark-dispatched
     :value      (cond-> {:heat-id subject} handoff (assoc :handoff handoff))
     :stake      :actuation/dispatch-heat
     :confidence (if (and a (not (registry/heat-chemistry-out-of-range? a))) 0.9 0.3)}))

(defn- propose-mill-cert
  "Draft the actual AIRWORTHINESS-EVIDENCE action -- issuing real
  class evidence certifying a heat as mill-cert-worthy.
  ALWAYS `:stake :actuation/issue-mill-cert` -- this is a
  REAL-WORLD safety-critical act, never a draft the actor may auto-run.
  See README `Actuation`: no phase ever adds this op to a phase's
  `:auto` set (`steelworks.phase`); the governor also always escalates
  on `:actuation/issue-mill-cert`. Two independent layers
  agree, deliberately."
  [db {:keys [subject]}]
  (let [a (store/heat db subject)]
    {:summary    (str subject " 向けミル規格証拠発行提案"
                      (when a (str " (block=" (:unit-name a) ")")))
     :rationale  (if a
                   "jurisdiction-evidence-checklist referenced"
                   "ブロック記録が見つかりません")
     :cites      (if a [subject] [])
     :effect     :heat/mark-certified
     :value      {:heat-id subject}
     :stake      :actuation/issue-mill-cert
     :confidence (if a 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :heat/intake                            (normalize-intake db request)
    :mill-rules/verify                        (verify-requirements db request)
    :quality/screen                                 (screen-quality-defect db request)
    :actuation/dispatch-heat                 (propose-block-dispatch db request)
    :actuation/issue-mill-cert      (propose-mill-cert db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは鉄鋼製造所のブロック実行・ミル規格証拠発行エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。説明や前置きは"
       "一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:heat/upsert|:verification/set|:quality-screen/set|"
       ":heat/mark-dispatched|:heat/mark-certified) "
       ":stake(:actuation/dispatch-heat か :actuation/issue-mill-cert か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :mill-rules/verify                    {:heat (store/heat st subject)}
    :quality/screen                              {:heat (store/heat st subject)}
    :actuation/dispatch-heat             {:heat (store/heat st subject)}
    :actuation/issue-mill-cert  {:heat (store/heat st subject)}
    {:heat (store/heat st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Steelworks Manufacturing
  Governor escalates/holds -- an LLM hiccup can never auto-dispatch an
  heat action or auto-issue class evidence."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :steelworksadvisor-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
