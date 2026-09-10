(ns landscapecare.phase-test
  (:require [clojure.test :refer [deftest is testing]]
            [landscapecare.phase :as phase]))

;; ──────────────────────── Phase Validity ──────────────────────

(deftest valid-phase-test
  (testing "intake is valid"
    (is (true? (phase/valid-phase? :intake))))

  (testing "match is valid"
    (is (true? (phase/valid-phase? :match))))

  (testing "audit is valid"
    (is (true? (phase/valid-phase? :audit))))

  (testing "invalid phase returns false"
    (is (false? (phase/valid-phase? :invalid)))))

;; ──────────────────────── Phase Transitions ──────────────────────

(deftest can-transition-test
  (testing "intake -> register is valid (forward progression)"
    (is (true? (phase/can-transition? :intake :register))))

  (testing "intake -> match is valid (skip register)"
    (is (true? (phase/can-transition? :intake :match))))

  (testing "register -> intake is invalid (backward)"
    (is (false? (phase/can-transition? :register :intake))))

  (testing "dispatch -> audit is valid (forward to end)"
    (is (true? (phase/can-transition? :dispatch :audit))))

  (testing "audit -> intake is invalid (backward from end)"
    (is (false? (phase/can-transition? :audit :intake))))

  (testing "same phase is invalid"
    (is (false? (phase/can-transition? :dispatch :dispatch))))

  (testing "invalid phases return false"
    (is (false? (phase/can-transition? :invalid :dispatch)))
    (is (false? (phase/can-transition? :dispatch :invalid)))))
