(ns insurance.governor
  "InsuranceBrokerageGovernor — the independent safety/traceability
  layer named in this repository's README/business-model.md, gating
  every policy binding an advisor may propose for an application. The
  governor never dispatches hardware itself and never binds a policy
  above a client's registered coverage-limit ceiling. Modeled on
  cloud-itonami-isco-4311's bookkeeping.governor. Task twist: a
  proposed coverage amount is an arithmetic ceiling against the
  application's registered coverage limit, and a policy cannot be
  bound until a risk disclosure has been attached.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. client provenance   — the individual/small-business applicant
                             must be registered.
    2. no-actuation        — proposal :effect must be :propose (the
                             governor never dispatches hardware and
                             never binds a policy above the
                             registered coverage-limit ceiling; it
                             only gates what the advisor may bind).
    3. application basis   — a policy-binding proposal must cite a
                             REGISTERED application belonging to this
                             client.
    4. coverage-limit ceiling — the proposed coverage amount must not
                             exceed the application's registered
                             `:max-coverage-limit` (binding coverage
                             beyond the client's registered ceiling is
                             unauthorized underwriting, not generous
                             coverage).
    5. risk-disclosure attached — a policy cannot be bound until the
                             application has `:risk-disclosure-
                             attached?` true (binding a policy without
                             an attached risk disclosure is
                             undisclosed coverage, not efficient
                             service).
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off per
  business-model.md's Trust Controls — these are :high/
  :safety-critical regardless of confidence):
    6. :op :approve-over-limit-binding (no policy binding above the
                             client's registered coverage-limit
                             ceiling without the governor gate).
    7. :op :approve-claims-settlement (settling a claim always
                             requires human sign-off).
    8. low confidence (< `confidence-floor`)."
  (:require [insurance.store :as store]))

(def confidence-floor 0.6)

(def ^:private always-escalate-ops #{:approve-over-limit-binding
                                     :approve-claims-settlement})

(defn- hard-violations [{:keys [request proposal]} client-record a]
  (let [{:keys [op coverage-amount]} proposal
        bind? (= :approve-policy-binding op)]
    (cond-> []
      (nil? client-record)
      (conj {:rule :no-client :detail "未登録 client"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（governor は登録上限超過の保険引受を直接実行しない）"})

      (and bind? (nil? a))
      (conj {:rule :unknown-application :detail "未登録 application への引受提案は不可"})

      (and bind? a (not= (:client-id a) (:client-id request)))
      (conj {:rule :application-wrong-client :detail "application が別 client のもの"})

      (and bind? a (number? coverage-amount) (> coverage-amount (:max-coverage-limit a)))
      (conj {:rule :coverage-exceeds-limit
             :detail (str "補償額 " coverage-amount " > 登録済み補償上限 "
                          (:max-coverage-limit a) "（登録上限を超える引受は無許可引受であって寛大な補償ではない）")})

      (and bind? a (not (:risk-disclosure-attached? a)))
      (conj {:rule :missing-risk-disclosure
             :detail "リスク開示が添付されていない引受は未開示補償であって効率的サービスではない"}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `insurance.store/Store`. Pure — never mutates
  the store, never binds a policy above the registered coverage
  limit."
  [request context proposal store]
  (let [client-record (store/client store (:client-id request))
        a (some->> (:application-id proposal) (store/application store))
        hard (hard-violations {:request request :proposal proposal}
                              client-record a)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        always-risky? (contains? always-escalate-ops (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not always-risky?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? always-risky?))}))
