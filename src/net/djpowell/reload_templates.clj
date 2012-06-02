(ns net.djpowell.reload-templates
  (:require [clojure.java.io :as io]))

;; Ring middleware that reloads namespaces when associated templates
;; change.  Assumes a hypothetical version of enlive that stores the
;; template path against the deftemplate/defsnippet var under the key:
;; :net.cgrand.enlive-html/template

(defn ^:private ns-templates
  "Get the list of templates and snippets associated with a given namespace"
  [ns]
  (keep #(:net.cgrand.enlive-html/template (meta %))
	(vals (ns-map ns))))

(defn ^:private template-ns-map
  "Generate a map of templates to namespaces for all templates
  associated with any of the given namespaces"
  [ns-list]
  (reduce (fn [m [t n]] (update-in m [t :namespaces] conj n))
	  {}
	  (for [ns ns-list
		template (ns-templates ns)]
	    [template ns])))

(defn ^:private last-modified
  "Get the last modified date in milliseconds from a template path"
  [template]
  (.getLastModified
   (.openConnection
    (io/resource template))))

(defn ^:private template-date-map
  "Generate a map of templates to last-modified-dates for all
  templates associated with any of the given namespaces"
  [ns-list]
  (into {}
	(for [ns ns-list
	      template (ns-templates ns)]
	  [template {:date (last-modified template)}])))

(defn ^:private template-map
  "Generate a map of templates to dates and namespaces"
  [ns-list]
  (merge-with merge (template-ns-map ns-list)
	      (template-date-map ns-list)))

; keeps track of the previous states of the templates
(def ^:private old-map-atom (atom {}))

(defn ^:private find-stale-templates
  "Returns a list of templates that have been changed since the last check"
  [ns-list new-map]
  (let [old-map @old-map-atom
	all-templates (seq (into #{} (concat (keys new-map) (keys old-map))))]
    (remove nil?
	    (for [template all-templates]
	      (cond
	       (not (old-map template))
	       template ; if newly encountered, then stale
	       ;;
	       (not (new-map template))
	       nil ; if not in changed set, then not stale
	       ;;
	       :else
	       (let [old-date (:date (old-map template))
		     new-date (:date (new-map template))]
		 (if (= old-date new-date)
		   nil
		   template)))))))  ; if date changed, then stale
	  
(defn ^:private find-stale-namespaces
  "Returns a list of namespaces that have templates that have changed since the last check"
  [ns-list new-map]
  (into #{}
	(mapcat :namespaces
		(map new-map (find-stale-templates ns-list new-map)))))

(defn ^:private reload-stale-templates
  "Reloads any namespaces that have templates that have changed since the last call"
  [ns-list]
  (let [new-map (template-map ns-list)
	stale-list (find-stale-namespaces ns-list new-map)]
    (swap! old-map-atom merge new-map)
    (doseq [ns stale-list]
      (try
	(require ns :reload)
	(catch java.io.IOException e
	  nil) ;failed to reload - ignore
	))))

(defn wrap-reload-templates
  "Ring middleware which takes a list of templates, and reloads those
  namespaces if any associated templates have changed"
  [handler ns-list]
  (fn [req]
    (reload-stale-templates ns-list)
    (handler req)))

