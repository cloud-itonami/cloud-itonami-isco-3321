(ns insurance.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [insurance.actor :as actor]
            [insurance.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Insurance"})
    (store/register-application! st {:application-id "P-1" :client-id "client-1"
                                     :name "application-042"
                                     :max-coverage-limit 500000
                                     :risk-disclosure-attached? true})
    st))

(deftest commits-a-within-limit-disclosed-binding
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-policy-binding :stake :low
                 :application-id "P-1" :coverage-amount 250000}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "client-1"))))))

(deftest holds-an-over-limit-binding
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-policy-binding :stake :low
                 :application-id "P-1" :coverage-amount 5000000}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "client-1")))))

(deftest interrupts-then-approves-over-limit-binding-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-over-limit-binding :stake :low
                 :application-id "P-1"}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "client-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "client-1")))))))
