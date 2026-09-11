(ns steelworks.export-run
  "CLI: write a demo audit CSV bundle for social / regulatory hand-off.
  Usage: clojure -M:dev:export [out-dir]
  Default out-dir: out/audit-package"
  (:require [steelworks.store :as store]
            [steelworks.export :as export]
            [steelworks.operation :as op]
            [langgraph.graph :as g]))

(def ^:private operator
  {:actor-id "op-1" :actor-role :mill-metallurgist :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "export-runner"}}
          {:thread-id tid :resume? true}))

(defn- seed-demo!
  []
  (let [db (store/seed-db)
        actor (op/build db)]
    (exec! actor "v" {:op :mill-rules/verify :subject "heat-1"})
    (approve! actor "v")
    (exec! actor "d" {:op :actuation/dispatch-heat :subject "heat-1"})
    (approve! actor "d")
    db))

(defn -main [& args]
  (let [dir (or (first args) "out/audit-package")
        db (seed-demo!)
        path (export/write-csv-bundle! db dir)
        pkg (export/audit-package db)]
    (println "wrote" path)
    (println "counts" (:counts pkg))
    (println "files" (vec (keys (export/package->csv-bundle db))))))
