package onlexnet.app.ports.in.admin;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Channel-agnostic outcome emitted by admin action handling.
 */
public sealed interface AdminOutcome permits AdminOutcome.ImmediateReply, AdminOutcome.BusinessOutcome,
        AdminOutcome.TechnicalOutcome {

    OutcomeCategory category();

    default DeliveryPolicy deliveryPolicy() {
        return switch (this) {
            case NoReply ignored -> DeliveryPolicy.NO_REPLY;
            case ImmediateReply ignored -> DeliveryPolicy.IMMEDIATE_REPLY;
            case DeferredReply ignored -> DeliveryPolicy.DEFERRED_REPLY;
        };
    }

    /**
     * Delivery policy expected from inbound adapters.
     */
    enum DeliveryPolicy {
        NO_REPLY,
        IMMEDIATE_REPLY,
        DEFERRED_REPLY
    }

    /**
     * Classifies whether an outcome is business-level or technical-failure level.
     */
    enum OutcomeCategory {
        BUSINESS,
        TECHNICAL
    }

    /**
     * Marker for outcomes classified as business-level.
     */
    sealed interface BusinessOutcome extends AdminOutcome permits NoReply, BusinessImmediateReply, DeferredReply {

        @Override
        default OutcomeCategory category() {
            return OutcomeCategory.BUSINESS;
        }
    }

    /**
     * Marker for outcomes classified as technical-failure level.
     */
    sealed interface TechnicalOutcome extends AdminOutcome permits TechnicalImmediateReply {

        @Override
        default OutcomeCategory category() {
            return OutcomeCategory.TECHNICAL;
        }
    }

    /**
     * No outbound user message should be sent.
     */
    sealed interface NoReply extends BusinessOutcome permits NoopIgnored {
    }

    record NoopIgnored() implements NoReply {
    }

    /**
     * Outbound user message should be sent right away.
     */
    sealed interface ImmediateReply extends AdminOutcome permits BusinessImmediateReply, TechnicalImmediateReply {
    }

    sealed interface BusinessImmediateReply extends ImmediateReply, BusinessOutcome permits Unauthorized,
            HelpOverview,
            DataEmpty,
            DataSummary,
            CollectTermMissing,
            CollectSuccess,
            PublishDisabled,
            PublishAlreadyDone,
            PublishNoData,
            PublishSuccess,
            UnknownAction {
    }

    sealed interface TechnicalImmediateReply extends ImmediateReply, TechnicalOutcome
            permits CollectFailure, PublishFailure {
    }

    record Unauthorized() implements BusinessImmediateReply {
    }

    record HelpOverview() implements BusinessImmediateReply {
    }

    record DataEmpty() implements BusinessImmediateReply {
    }

    record DataSummary(
            int termNum,
            LocalDate from,
            Optional<LocalDate> to,
            int termCount) implements BusinessImmediateReply {

        public DataSummary {
            to = to == null ? Optional.empty() : to;
        }
    }

    record CollectTermMissing() implements BusinessImmediateReply {
    }

    record CollectSuccess(
            LocalDate date,
            int termNum,
            int total,
            int votings,
            int committeeSittings,
            int prints,
            int interpellations,
            int writtenQuestions,
            int bills) implements BusinessImmediateReply {
    }

    record CollectFailure(
            String reason) implements TechnicalImmediateReply {
    }

    record PublishDisabled() implements BusinessImmediateReply {
    }

    record PublishAlreadyDone(
            LocalDate date) implements BusinessImmediateReply {
    }

    record PublishNoData(
            LocalDate date) implements BusinessImmediateReply {
    }

    record PublishSuccess(
            LocalDate date) implements BusinessImmediateReply {
    }

    record PublishFailure(
            String reason) implements TechnicalImmediateReply {
    }

    record UnknownAction(
            String command) implements BusinessImmediateReply {
    }

    /**
     * Work is deferred and adapter should send an acknowledgement.
     */
    sealed interface DeferredReply extends BusinessOutcome permits ActionDeferred {
    }

    record ActionDeferred(
            String correlationId) implements DeferredReply {
    }
}
