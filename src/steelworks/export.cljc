(ns steelworks.export
  "Audit-package export for social / regulatory hand-off.

  Produces plain EDN maps and CSV strings over a `steelworks.store/Store`
  snapshot -- the same append-only ledger, block-dispatch drafts and
  mill-cert drafts the governor writes. Pure data transforms only:
  no I/O, no network, no signature. The mill's own act is to sign and
  file the package; this namespace only materializes the package body.

  This is the honest delivery of the industry-stack `:export?` contract
  (robotics / audit-ledger capabilities) for ISIC 2410."
  (:require [clojure.string :as str]
            [steelworks.store :as store]))

(defn- csv-escape [v]
  (let [s (str (if (nil? v) "" v))]
    (if (re-find #"[,\"\n\r]" s)
      (str "\"" (str/replace s "\"" "\"\"") "\"")
      s)))

(defn- csv-row [cols]
  (str/join "," (map csv-escape cols)))

(defn ledger-rows
  "Normalize ledger facts into flat row maps suitable for CSV."
  [st]
  (mapv (fn [i f]
          {:seq i
           :t (:t f)
           :op (str (:op f))
           :actor (:actor f)
           :subject (:subject f)
           :disposition (str (:disposition f))
           :basis (pr-str (:basis f))
           :summary (:summary f)})
        (range)
        (store/ledger st)))

(defn dispatch-rows [st]
  (mapv (fn [i r]
          {:seq i
           :record_id (get r "record_id")
           :kind (get r "kind")
           :heat_id (get r "heat_id")
           :jurisdiction (get r "jurisdiction")})
        (range)
        (store/dispatch-history st)))

(defn evidence-rows [st]
  (mapv (fn [i r]
          {:seq i
           :record_id (get r "record_id")
           :kind (get r "kind")
           :heat_id (get r "heat_id")
           :jurisdiction (get r "jurisdiction")})
        (range)
        (store/evidence-history st)))

(defn heats-snapshot [st]
  (mapv (fn [b]
          (select-keys b [:id :unit-name :jurisdiction :status
                          :chemistry-deviation-actual
                          :chemistry-deviation-min
                          :chemistry-deviation-max
                          :quality-defect-unresolved?
                          :heat-dispatched?
                          :mill-certified?
                          :dispatch-number
                          :evidence-number]))
        (store/all-heats st)))

(defn audit-package
  "Full audit package for a store snapshot -- the body a steelworks would
  hand to mill inspectors, standards-body inspectors or internal compliance.
  `:format` is always `:edn-maps` for the nested package; use
  `package->csv-bundle` for CSV strings."
  [st]
  {:isic "2410"
   :business-id "cloud-itonami-isic-2410"
   :format :edn-maps
   :heats (heats-snapshot st)
   :ledger (vec (store/ledger st))
   :dispatches (vec (store/dispatch-history st))
   :mill-cert (vec (store/evidence-history st))
   :counts {:heats (count (store/all-heats st))
            :ledger (count (store/ledger st))
            :dispatches (count (store/dispatch-history st))
            :mill-cert (count (store/evidence-history st))}})

(defn rows->csv
  "Render a seq of flat maps as CSV using `header` column order."
  [header rows]
  (let [lines (into [(csv-row (map name header))]
                    (map (fn [r] (csv-row (map #(get r %) header))) rows))]
    (str (str/join "\n" lines) (when (seq lines) "\n"))))

(defn package->csv-bundle
  "CSV bundle for spreadsheet hand-off. Keys are filenames; values are
  CSV body strings."
  [st]
  {"heats.csv" (rows->csv [:id :unit-name :jurisdiction :status
                            :chemistry-deviation-actual
                            :heat-dispatched? :mill-certified?
                            :dispatch-number :evidence-number]
                           (heats-snapshot st))
   "ledger.csv" (rows->csv [:seq :t :op :actor :subject :disposition :basis :summary]
                           (ledger-rows st))
   "dispatches.csv" (rows->csv [:seq :record_id :kind :heat_id :jurisdiction]
                               (dispatch-rows st))
   "mill-cert.csv" (rows->csv [:seq :record_id :kind :heat_id :jurisdiction]
                                   (evidence-rows st))})
