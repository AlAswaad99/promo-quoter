package com.promoquoter.pricing;

import java.math.BigDecimal;
import java.util.UUID;

public sealed interface ActionSpec {

    ActionKind kind();

    record PercentOff(BigDecimal percent) implements ActionSpec {

        @Override
        public ActionKind kind() {
            return ActionKind.PERCENT_OFF;
        }
    }

    record AmountOff(BigDecimal amount) implements ActionSpec {

        @Override
        public ActionKind kind() {
            return ActionKind.AMOUNT_OFF;
        }
    }

    record FreeUnits(int buyQty, int freeQty) implements ActionSpec {

        @Override
        public ActionKind kind() {
            return ActionKind.FREE_UNITS;
        }
    }

    record AmountOffCart(BigDecimal amount) implements ActionSpec {

        @Override
        public ActionKind kind() {
            return ActionKind.AMOUNT_OFF_CART;
        }
    }

    record Surcharge(BigDecimal amount) implements ActionSpec {

        @Override
        public ActionKind kind() {
            return ActionKind.SURCHARGE;
        }
    }

    record ShippingWaiver() implements ActionSpec {

        @Override
        public ActionKind kind() {
            return ActionKind.SHIPPING_WAIVER;
        }
    }

    record FreeGift(UUID productId, int qty) implements ActionSpec {

        @Override
        public ActionKind kind() {
            return ActionKind.FREE_GIFT;
        }
    }

    record Reject(String message) implements ActionSpec {

        @Override
        public ActionKind kind() {
            return ActionKind.REJECT;
        }
    }
}
