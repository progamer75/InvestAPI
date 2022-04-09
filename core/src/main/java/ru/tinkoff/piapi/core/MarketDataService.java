package ru.tinkoff.piapi.core;

import io.grpc.stub.StreamObserver;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.BackPressureStrategy;
import org.reactivestreams.FlowAdapters;
import ru.tinkoff.piapi.contract.v1.CandleInterval;
import ru.tinkoff.piapi.contract.v1.GetCandlesRequest;
import ru.tinkoff.piapi.contract.v1.GetCandlesResponse;
import ru.tinkoff.piapi.contract.v1.GetLastPricesRequest;
import ru.tinkoff.piapi.contract.v1.GetLastPricesResponse;
import ru.tinkoff.piapi.contract.v1.GetOrderBookRequest;
import ru.tinkoff.piapi.contract.v1.GetOrderBookResponse;
import ru.tinkoff.piapi.contract.v1.GetTradingStatusRequest;
import ru.tinkoff.piapi.contract.v1.GetTradingStatusResponse;
import ru.tinkoff.piapi.contract.v1.HistoricCandle;
import ru.tinkoff.piapi.contract.v1.LastPrice;
import ru.tinkoff.piapi.contract.v1.MarketDataRequest;
import ru.tinkoff.piapi.contract.v1.MarketDataResponse;
import ru.tinkoff.piapi.core.stream.MarketDataSubscriptionService;
import ru.tinkoff.piapi.core.utils.DateUtils;
import ru.tinkoff.piapi.core.utils.Helpers;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static ru.tinkoff.piapi.contract.v1.MarketDataServiceGrpc.MarketDataServiceBlockingStub;
import static ru.tinkoff.piapi.contract.v1.MarketDataServiceGrpc.MarketDataServiceStub;
import static ru.tinkoff.piapi.contract.v1.MarketDataStreamServiceGrpc.MarketDataStreamServiceStub;
import static ru.tinkoff.piapi.core.utils.Helpers.unaryCall;

public class MarketDataService {
  private final MarketDataStreamServiceStub marketDataStreamStub;
  private final MarketDataServiceBlockingStub marketDataBlockingStub;
  private final MarketDataServiceStub marketDataStub;

  MarketDataService(@Nonnull MarketDataStreamServiceStub marketDataStreamStub,
                    @Nonnull MarketDataServiceBlockingStub marketDataBlockingStub,
                    @Nonnull MarketDataServiceStub marketDataStub) {
    this.marketDataStreamStub = marketDataStreamStub;
    this.marketDataBlockingStub = marketDataBlockingStub;
    this.marketDataStub = marketDataStub;
  }

  @Nonnull
  public List<HistoricCandle> getCandlesSync(@Nonnull String figi,
                                             @Nonnull Instant from,
                                             @Nonnull Instant to,
                                             @Nonnull CandleInterval interval) {
    return unaryCall(() -> marketDataBlockingStub.getCandles(
        GetCandlesRequest.newBuilder()
          .setFigi(figi)
          .setFrom(DateUtils.instantToTimestamp(from))
          .setTo(DateUtils.instantToTimestamp(to))
          .setInterval(interval)
          .build())
      .getCandlesList());
  }

  @Nonnull
  public List<LastPrice> getLastPricesSync(@Nonnull Iterable<String> figies) {
    return unaryCall(() -> marketDataBlockingStub.getLastPrices(
        GetLastPricesRequest.newBuilder()
          .addAllFigi(figies)
          .build())
      .getLastPricesList());
  }

  @Nonnull
  public GetOrderBookResponse getOrderBookSync(@Nonnull String figi, int depth) {
    return unaryCall(() -> marketDataBlockingStub.getOrderBook(
      GetOrderBookRequest.newBuilder()
        .setFigi(figi)
        .setDepth(depth)
        .build()));
  }

  @Nonnull
  public GetTradingStatusResponse getTradingStatusSync(@Nonnull String figi) {
    return unaryCall(() -> marketDataBlockingStub.getTradingStatus(
      GetTradingStatusRequest.newBuilder()
        .setFigi(figi)
        .build()));
  }

  @Nonnull
  public CompletableFuture<List<HistoricCandle>> getCandles(@Nonnull String figi,
                                                            @Nonnull Instant from,
                                                            @Nonnull Instant to,
                                                            @Nonnull CandleInterval interval) {
    return Helpers.<GetCandlesResponse>unaryAsyncCall(
        observer -> marketDataStub.getCandles(
          GetCandlesRequest.newBuilder()
            .setFigi(figi)
            .setFrom(DateUtils.instantToTimestamp(from))
            .setTo(DateUtils.instantToTimestamp(to))
            .setInterval(interval)
            .build(),
          observer))
      .thenApply(GetCandlesResponse::getCandlesList);
  }

  @Nonnull
  public CompletableFuture<List<LastPrice>> getLastPrices(@Nonnull Iterable<String> figies) {
    return Helpers.<GetLastPricesResponse>unaryAsyncCall(
        observer -> marketDataStub.getLastPrices(
          GetLastPricesRequest.newBuilder()
            .addAllFigi(figies)
            .build(),
          observer))
      .thenApply(GetLastPricesResponse::getLastPricesList);
  }

  @Nonnull
  public CompletableFuture<GetOrderBookResponse> getOrderBook(@Nonnull String figi, int depth) {
    return Helpers.unaryAsyncCall(
      observer -> marketDataStub.getOrderBook(
        GetOrderBookRequest.newBuilder()
          .setFigi(figi)
          .setDepth(depth)
          .build(),
        observer));
  }

  @Nonnull
  public CompletableFuture<GetTradingStatusResponse> getTradingStatus(@Nonnull String figi) {
    return Helpers.unaryAsyncCall(
      observer -> marketDataStub.getTradingStatus(
        GetTradingStatusRequest.newBuilder()
          .setFigi(figi)
          .build(),
        observer));
  }
}
