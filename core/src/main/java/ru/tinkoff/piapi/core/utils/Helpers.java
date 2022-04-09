package ru.tinkoff.piapi.core.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.smallrye.mutiny.subscription.MultiEmitter;
import org.apache.commons.io.IOUtils;
import ru.tinkoff.piapi.core.exception.ApiRuntimeException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Helpers {

  private static final Map<String, HashMap<String, String>> errorsMap = new HashMap<>();
  private static final String DEFAULT_ERROR_ID = "70001";
  private static final String TRACKING_ID_HEADER = "x-tracking-id";

  static {
    try {
      InputStream resourceAsStream = Helpers.class.getClassLoader().getResourceAsStream("errors.json");
      if (resourceAsStream == null) {
        throw new RuntimeException("Не найден файл errors.json");
      }
      String json = new String(IOUtils.toByteArray(resourceAsStream)/*resourceAsStream.readAllBytes()*/, StandardCharsets.UTF_8);
      errorsMap.putAll(new ObjectMapper().readValue(json, new TypeReference<Map<String, HashMap<String, String>>>() {
      }));
    } catch (IOException e) {
      throw new RuntimeException("Не найден файл errors.json");
    }
  }

/*  public static byte[] toByteArray(final InputStream inputStream) throws IOException {
    // We use a ThresholdingOutputStream to avoid reading AND writing more than Integer.MAX_VALUE.
    try (final UnsynchronizedByteArrayOutputStream ubaOutput = new UnsynchronizedByteArrayOutputStream();
         final ThresholdingOutputStream thresholdOuput = new ThresholdingOutputStream(Integer.MAX_VALUE, os -> {
           throw new IllegalArgumentException(
             String.format("Cannot read more than %,d into a byte array", Integer.MAX_VALUE));
         }, os -> ubaOutput)) {
      copy(inputStream, thresholdOuput);
      return ubaOutput.toByteArray();
    }
  }*/

  public static <T> T unaryCall(Supplier<T> supplier) {
    try {
      return supplier.get();
    } catch (Exception exception) {
      throw apiRuntimeException(exception);
    }
  }

  private static ApiRuntimeException apiRuntimeException(Throwable exception) {
    Status status = Status.fromThrowable(exception);
    String id = getErrorId(status);
    String description = getErrorDescription(id);
    String trackingId = getTrackingId(exception);
    return new ApiRuntimeException(description, id, exception, trackingId);
  }

  private static String getTrackingId(Throwable exception) {
    if (!(exception instanceof StatusRuntimeException)) {
      return null;
    }

    Metadata trailers = ((StatusRuntimeException) exception).getTrailers();
    if (trailers == null) {
      return null;
    }

    return trailers.get(Metadata.Key.of(TRACKING_ID_HEADER, Metadata.ASCII_STRING_MARSHALLER));
  }

  private static String getErrorId(Status status) {
    if ("RESOURCE_EXHAUSTED".equals(status.getCode().name())) {
      return "80002";
    }
    String error = status.getDescription();
    if(!Objects.isNull(error))
      return error;
    else
      return DEFAULT_ERROR_ID;
      //requireNonNullElse(error, DEFAULT_ERROR_ID);
  }

  /**
   * Связывание асинхронного Unary-вызова с {@link CompletableFuture}.
   *
   * @param callPerformer Асинхронный Unary-вызов.
   * @param <T>           Тип результата вызова.
   * @return {@link CompletableFuture} с результатом вызова.
   */
  public static <T> CompletableFuture<T> unaryAsyncCall(Consumer<StreamObserver<T>> callPerformer) {
    CompletableFuture<T> cf = new CompletableFuture<T>();
    callPerformer.accept(mkStreamObserverWithFuture(cf));
    return cf;
  }

  /**
   * Создание StreamObserver, который связывает свой результат с CompletableFuture.
   * <p>
   * Только для Unary-вызовов!
   */
  private static <T> StreamObserver<T> mkStreamObserverWithFuture(CompletableFuture<T> cf) {
    return new StreamObserver<T>() {

      @Override
      public void onNext(T value) {
        cf.complete(value);
      }

      @Override
      public void onError(Throwable t) {
        ApiRuntimeException throwable = apiRuntimeException(t);
        cf.completeExceptionally(throwable);
      }

      @Override
      public void onCompleted() {
      }
    };
  }

  /**
   * Связывание {@link MultiEmitter} со {@link StreamObserver}.
   *
   * @param emitter Экземпляр {@link MultiEmitter}.
   * @param <T>     Тип оперируемый {@link MultiEmitter}.
   * @return Связанный {@link StreamObserver}.
   */
  public static <T> StreamObserver<T> wrapEmitterWithStreamObserver(MultiEmitter<? super T> emitter) {
    return new StreamObserver<T>() {
      @Override
      public void onNext(T value) {
        emitter.emit(value);
      }

      @Override
      public void onError(Throwable t) {
        emitter.fail(t);
      }

      @Override
      public void onCompleted() {
        emitter.complete();
      }
    };
  }

  /**
   * Проведение необходимых преобразований для пользовательского идентификатора поручения.
   *
   * @param orderId Пользовательский идентификатор поручения.
   * @return Преобразованный идентификатор поручения.
   */
  public static String preprocessInputOrderId(String orderId) {
    int maxLength = Math.min(orderId.length(), 36);
    if(orderId.isEmpty() || orderId.trim().isEmpty())
      return "";
    else
      return orderId.substring(0, maxLength);
    //return orderId.isBlank() ? orderId.trim() : orderId.substring(0, maxLength);
  }

  private static String getErrorDescription(String id) {
    return errorsMap.get(id).get("description");
  }
}
