/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.trade;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Peer;
import io.bitsquare.storage.Storage;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.trade.protocol.trade.BuyerProtocol;
import io.bitsquare.trade.states.BuyerTradeState;
import io.bitsquare.trade.states.TradeState;

import org.bitcoinj.core.Coin;

import java.io.Serializable;

import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BuyerTrade extends Trade implements Serializable {
    // That object is saved to disc. We need to take care of changes to not break deserialization.
    private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

    transient private static final Logger log = LoggerFactory.getLogger(BuyerAsOffererTrade.class);

    public BuyerTrade(Offer offer, Coin tradeAmount, Peer tradingPeer, Storage<? extends TradableList> storage) {
        super(offer, tradeAmount, tradingPeer, storage);
        log.trace("Created by constructor");
    }

    public BuyerTrade(Offer offer, Storage<? extends TradableList> storage) {
        super(offer, storage);
        log.trace("Created by constructor");
    }

    @Override
    protected void initStates() {
        if (processState == null)
            processState = BuyerTradeState.ProcessState.UNDEFINED;
        if (lifeCycleState == null)
            lifeCycleState = Trade.LifeCycleState.PREPARATION;
        initStateProperties();
    }

    public void onFiatPaymentStarted() {
        assert tradeProtocol instanceof BuyerProtocol;
        ((BuyerProtocol) tradeProtocol).onFiatPaymentStarted();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setter for Mutable objects
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void setProcessState(TradeState.ProcessState processState) {
        super.setProcessState(processState);

        switch ((BuyerTradeState.ProcessState) processState) {
            case DEPOSIT_PUBLISHED:
                takeOfferDate = new Date();

                if (this instanceof OffererTrade)
                    openOfferManager.closeOpenOffer(getOffer());
                break;

            case TIMEOUT:
                disposeProtocol();
                setLifeCycleState(Trade.LifeCycleState.FAILED);

                tradeManager.removeFailedTrade(this);
                break;

            case FAULT:
                disposeProtocol();
                setLifeCycleState(Trade.LifeCycleState.FAILED);

                tradeManager.removeFailedTrade(this);
                break;
        }
    }

    @Override
    public void setLifeCycleState(Trade.LifeCycleState lifeCycleState) {
        super.setLifeCycleState(lifeCycleState);

        switch (lifeCycleState) {
            case FAILED:
                disposeProtocol();
                break;
            case COMPLETED:
                disposeProtocol();
                break;
        }
    }

    @Override
    public void setThrowable(Throwable throwable) {
        super.setThrowable(throwable);

        //  setProcessState(BuyerTradeState.ProcessState.EXCEPTION);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void handleConfidenceResult() {
        if (((BuyerTradeState.ProcessState) processState).ordinal() < BuyerTradeState.ProcessState.DEPOSIT_CONFIRMED.ordinal())
            setProcessState(BuyerTradeState.ProcessState.DEPOSIT_CONFIRMED);
    }
}
