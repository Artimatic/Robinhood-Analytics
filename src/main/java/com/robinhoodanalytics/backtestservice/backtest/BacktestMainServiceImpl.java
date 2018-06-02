package com.robinhoodanalytics.backtestservice.backtest;

import com.robinhoodanalytics.backtestservice.BacktestServiceApplication;
import com.robinhoodanalytics.backtestservice.models.Action;
import com.robinhoodanalytics.backtestservice.models.Quote;
import com.robinhoodanalytics.backtestservice.models.Signal;
import com.robinhoodanalytics.backtestservice.models.StockRank;
import com.robinhoodanalytics.backtestservice.quotes.QuoteService;
import com.robinhoodanalytics.backtestservice.strategy.BuyAndHold;
import com.robinhoodanalytics.backtestservice.utils.RollingAverage;
import com.robinhoodanalytics.backtestservice.utils.Statistics;
import org.apache.tomcat.jni.Error;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Component("backtestMainService")
public class BacktestMainServiceImpl
    implements BacktestMainService
{
    @Autowired
    QuoteService _quoteService;

    private static final Logger log = LoggerFactory.getLogger(BacktestServiceApplication.class);

    @Override
    public List<Signal> executeMeanReversion(String symbol, Date from, Date to, BigDecimal deviation, int shortTerm, int longTerm)
            throws Exception
    {
        return trainMeanReversion(symbol, from, to, deviation, shortTerm, longTerm);
    }

    @Override
    public List<Signal> buyAndHold(List<String> symbols, Date from, Date to, BigDecimal initialFund)
    {
        List<StockRank> stocks = new ArrayList<>();
        for(String symbol: symbols) {
            stocks.add(new StockRank(symbol, 1.0));
        }
        BacktestContext context = new BacktestContext(stocks, initialFund);
        BuyAndHold buyHold = new BuyAndHold();

        buyHold.initialize(context);

        Map<String, List<Quote>> data = new HashMap<>();

        for(StockRank stock: stocks) {
            _quoteService.getHistoricalQuotes(stock.getSymbol(), from, to);
        }

        return new ArrayList<>();
    }

    private List<Signal> trainMeanReversion(String symbol,
                                           Date from,
                                           Date to,
                                           BigDecimal deviation,
                                           int shortTermWindow,
                                           int longTermWindow
    ) throws Exception
    {
        log.info("trainMeanReversion params: {} {} {}", symbol, from, to);

        List<Quote> quotes = _quoteService.getHistoricalQuotes(symbol, from, to);

        RollingAverage shortTerm = new RollingAverage(shortTermWindow);
        RollingAverage longTerm = new RollingAverage(longTermWindow);
        RollingAverage volumeWindow = new RollingAverage(longTermWindow);
        Deque<BigDecimal> recentVolumeChanges = new ArrayDeque<>();
        Deque<BigDecimal> recentPrices = new ArrayDeque<>();

        List<Signal> results = new ArrayList<>();

        int preload = 0;

        for (Quote quote : quotes) {
            shortTerm.add(quote.getClose());
            longTerm.add(quote.getClose());
            volumeWindow.add(new BigDecimal(quote.getVolume()));
            if (preload < longTermWindow) {
                preload++;
            } else {
                BigDecimal shortAvg = shortTerm.getAverage();
                BigDecimal longAvg = longTerm.getAverage();

                BigDecimal pctChange = Statistics.percentDifference(shortAvg, longAvg).abs();

                BigDecimal volumeChange = Statistics.percentChange(volumeWindow.getAverage(), new BigDecimal(quote.getVolume()));


                addToQueue(volumeChange, recentVolumeChanges, 10);
                addToQueue(quote.getClose(), recentPrices, 10);

                Action a = getMeanReversionDirection(quote.getClose(), shortAvg, longAvg, recentVolumeChanges, recentPrices);
                Signal sig = new Signal(quote.getDate(), a,
                        pctChange, shortAvg, longAvg, volumeChange, quote.getClose());
                results.add(sig);
            }
        }
        return results;
    }

    private Action getMeanReversionDirection(BigDecimal lastPrice, BigDecimal avg30, BigDecimal avg90,
                                             Deque<BigDecimal> volumeChanges, Deque<BigDecimal> prices) {
        Action recommendation = Action.INDETERMINANT;
        if (lastPrice.compareTo(avg30) < 0 && lastPrice.compareTo(avg90) < 0) {
            recommendation = Action.BUY;
        } else if (lastPrice.compareTo(avg30) > 0 && lastPrice.compareTo(avg90) > 0){
            recommendation = Action.SELL;
        }
        return recommendation;
    }

    private BigDecimal addToQueue(BigDecimal value, Deque<BigDecimal> queue, int maxSize) throws Exception{
        if (queue.size() < maxSize) {
            queue.push(value);
        } else if (queue.size() > maxSize) {
            throw new Exception("Too many items in queue");
        } else if (queue.size() == 90) {
            queue.push(value);
            return queue.removeFirst();
        }
        return null;
    }

    private void calculateReturns(List<Signal> signals, BigDecimal deviation) {
        int sellOrders
        for (Signal signal : signals) {
            if (Statistics.percentDifference(signal.getShortTermAverage(), signal.getLongTermAverage()).abs().compareTo(deviation) <= 0) {
                if (signal.getAction() == Action.SELL) {

                }
            }
        }
    }

    private class BacktestResults {
        
    }

}
