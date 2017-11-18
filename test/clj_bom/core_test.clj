(ns clj-bom.core-test
  (:require [clojure.test :refer :all]
            [clj-bom.core :refer :all]
            [clojure.java.io :as io])
  (:import (java.io StringWriter)))

(def ^:private test-str
  (str \uFEFF "whatever"))

(deftest bom-tests

  (testing "UTF-8"
    (let [test-str (.getBytes test-str "UTF-8")]
      (is (true? (has-utf8-bom? test-str)))))

  (testing "UTF-16LE"
    (let [test-str (.getBytes test-str "UTF-16LE")]
      (is (true? (has-bom? utf16-le-BOM test-str)))))

  (testing "UTF-16BE"
    (let [test-str (.getBytes test-str "UTF-16BE")]
      (is (true? (has-bom? utf16-be-BOM test-str)))))


  (testing "detect-charset"

    (let [test-str (.getBytes test-str "UTF-8")]
      (is (= "UTF-8" (detect-charset (io/input-stream test-str)))))
    (let [test-str (.getBytes test-str "UTF-16LE")]
      (is (= "UTF-16LE" (detect-charset (io/input-stream test-str)))))
    (let [test-str (.getBytes test-str "UTF-16BE")]
      (is (= "UTF-16BE" (detect-charset (io/input-stream test-str)))))
    (let [test-str (.getBytes test-str "UTF-32BE")]
      (is (= "UTF-32BE" (detect-charset (io/input-stream test-str)))))
    )


  (testing "bom-input-stream->reader"
    (with-open [rdr (bom-reader
                      (io/input-stream (.getBytes test-str "UTF-8")))
                wrt (StringWriter.)]
      (io/copy rdr wrt)
      (is (= (subs test-str 1)
             (.toString wrt))))

    (with-open [rdr (bom-reader
                      (io/input-stream (.getBytes test-str "UTF-16LE")))
                wrt (StringWriter.)]
      (io/copy rdr wrt)
      (is (= (subs test-str 1)
             (.toString wrt))))

    (with-open [rdr (bom-reader
                      (io/input-stream (.getBytes test-str "UTF-16BE")))
                wrt (StringWriter.)]
      (io/copy rdr wrt)
      (is (= (subs test-str 1)
             (.toString wrt))))

    )

  )

