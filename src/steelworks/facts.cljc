(ns steelworks.facts
  "Per-jurisdiction basic iron and steel mill-standards catalog -- the
  G2-style spec-basis table the Steelworks Governor checks every
  `:mill-rules/verify` proposal against.

  Coverage is reported HONESTLY: a jurisdiction not in this table has
  NO spec-basis. Seed values cite official steel / industrial standards
  authorities; this is a starting catalog, not a survey of every mill
  standard body.")

(def catalog
  {"JPN" {:name "Japan"
          :owner-authority "日本産業標準調査会 (JISC) / 日本鉄鋼連盟 (参考) / JIS 鋼材規格"
          :legal-basis "産業標準化法 / JIS G 系列 鋼材規格 (参考)"
          :national-spec "普通鋼・特殊鋼の製造・検査・ミルシート要件"
          :provenance "https://www.jisc.go.jp/"
          :required-evidence ["CAEシミュレーション報告書 (CAE-simulation-report)"
                              "成分分析報告書 (chemistry-analysis-report)"
                              "品質検査連鎖記録 (quality-chain-of-custody-record)"
                              "材料証明記録 (material-certification-record)"]}
   "USA" {:name "United States"
          :owner-authority "ASTM International / AISI (reference)"
          :legal-basis "ASTM A6 / A20 structural and plate steel specifications (reference)"
          :national-spec "US basic iron and steel product specification and mill-test requirements"
          :provenance "https://www.astm.org/"
          :required-evidence ["CAE-simulation-report"
                              "chemistry-analysis-report"
                              "quality-chain-of-custody-record"
                              "Material-certification-record"]}
   "GBR" {:name "United Kingdom"
          :owner-authority "BSI / EN steel product standards (UK adoption)"
          :legal-basis "BS EN 10025 / related structural steel standards (reference)"
          :national-spec "UK basic iron and steel product and mill-test requirements"
          :provenance "https://www.bsigroup.com/"
          :required-evidence ["CAE-simulation-report"
                              "chemistry-analysis-report"
                              "quality-chain-of-custody-record"
                              "Material-certification-record"]}
   "DEU" {:name "Germany"
          :owner-authority "DIN / EN steel product standards"
          :legal-basis "DIN EN 10025 / related steel product norms (reference)"
          :national-spec "DE basic iron and steel product and mill-test requirements"
          :provenance "https://www.din.de/"
          :required-evidence ["CAE-Simulationsbericht (CAE-simulation-report)"
                              "Chemieanalysebericht (chemistry-analysis-report)"
                              "Qualitäts-Rückverfolgbarkeitsnachweis (quality-chain-of-custody-record)"
                              "Werkstoffzertifikat (material-certification-record)"]}})

(defn spec-basis [iso3] (get catalog iso3))

(defn coverage
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-2410 R0: " (count catalog)
                 " jurisdictions seeded. Extend `steelworks.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
