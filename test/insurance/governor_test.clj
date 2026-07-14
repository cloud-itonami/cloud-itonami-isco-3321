(ns insurance.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [insurance.store :as store]
            [insurance.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Insurance"})
    (store/register-application! st {:application-id "P-1" :client-id "client-1"
                                     :name "application-042"
                                     :max-coverage-limit 500000
                                     :risk-disclosure-attached? true})
    st))

(defn- bind-op [amount]
  {:op :approve-policy-binding :effect :propose :application-id "P-1"
   :coverage-amount amount :confidence 0.9 :stake :low})

(def ^:private req {:client-id "client-1"})

(deftest ok-within-limit-and-disclosed
  (let [st (fresh-store)
        v (governor/check req {} (bind-op 250000) st)]
    (is (:ok? v))))

(deftest ok-at-exact-limit-boundary
  (testing "the coverage-limit ceiling is inclusive"
    (let [st (fresh-store)
          v (governor/check req {} (bind-op 500000) st)]
      (is (:ok? v)))))

(deftest hard-on-coverage-exceeds-limit
  (testing "binding coverage beyond the client's registered ceiling is unauthorized underwriting, not generous coverage"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (bind-op 5000000) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :coverage-exceeds-limit (:rule %)) (:violations v))))))

(deftest hard-on-missing-risk-disclosure
  (testing "binding a policy without an attached risk disclosure is undisclosed coverage, not efficient service"
    (let [st (store/mem-store)]
      (store/register-client! st {:client-id "client-1" :name "Kobo Insurance"})
      (store/register-application! st {:application-id "P-1" :client-id "client-1"
                                       :name "application-042"
                                       :max-coverage-limit 500000
                                       :risk-disclosure-attached? false})
      (let [v (governor/check req {} (assoc (bind-op 250000) :confidence 0.99) st)]
        (is (:hard? v))
        (is (some #(= :missing-risk-disclosure (:rule %)) (:violations v)))))))

(deftest hard-on-unknown-application
  (let [st (fresh-store)
        v (governor/check req {} (assoc (bind-op 250000) :application-id "P-ghost") st)]
    (is (:hard? v))
    (is (some #(= :unknown-application (:rule %)) (:violations v)))))

(deftest hard-on-foreign-application
  (let [st (fresh-store)]
    (store/register-client! st {:client-id "client-2" :name "Other"})
    (let [v (governor/check {:client-id "client-2"} {} (bind-op 250000) st)]
      (is (:hard? v))
      (is (some #(= :application-wrong-client (:rule %)) (:violations v))))))

(deftest hard-on-unregistered-client
  (let [st (fresh-store)
        v (governor/check {:client-id "nobody"} {} (bind-op 250000) st)]
    (is (:hard? v))
    (is (some #(= :no-client (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (bind-op 250000) :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest always-escalates-over-limit-binding-even-at-high-confidence
  (testing "no policy binding above the client's registered coverage-limit ceiling without the governor gate"
    (let [st (fresh-store)
          v (governor/check req {} {:op :approve-over-limit-binding :effect :propose
                                    :application-id "P-1" :confidence 0.99 :stake :low} st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest always-escalates-claims-settlement-even-at-high-confidence
  (testing "settling a claim always requires human sign-off"
    (let [st (fresh-store)
          v (governor/check req {} {:op :approve-claims-settlement :effect :propose
                                    :application-id "P-1" :confidence 0.99 :stake :low} st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (bind-op 250000) :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))
