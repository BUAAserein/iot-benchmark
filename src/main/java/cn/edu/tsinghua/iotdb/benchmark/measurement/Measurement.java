package cn.edu.tsinghua.iotdb.benchmark.measurement;

import cn.edu.tsinghua.iotdb.benchmark.client.Operation;
import cn.edu.tsinghua.iotdb.benchmark.conf.Config;
import cn.edu.tsinghua.iotdb.benchmark.conf.ConfigDescriptor;
import cn.edu.tsinghua.iotdb.benchmark.measurement.enums.Metric;
import cn.edu.tsinghua.iotdb.benchmark.measurement.enums.TotalOperationResult;
import cn.edu.tsinghua.iotdb.benchmark.measurement.enums.TotalResult;
import cn.edu.tsinghua.iotdb.benchmark.measurement.persistence.ITestDataPersistence;
import cn.edu.tsinghua.iotdb.benchmark.measurement.persistence.PersistenceFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Measurement {

  private static final Logger LOGGER = LoggerFactory.getLogger(Measurement.class);
  private static Config config = ConfigDescriptor.getInstance().getConfig();
  private static final double[] MID_AVG_RANGE = {0.1, 0.9};
  private Map<Operation, List<Double>> operationLatencies;
  private Map<Operation, List<Double>> getOperationLatencySumsList;
  private Map<Operation, Double> operationLatencySums;
  private double createSchemaTime;
  private double elapseTime;
  private Map<Operation, Long> okOperationNumMap;
  private Map<Operation, Long> failOperationNumMap;
  private Map<Operation, Long> okPointNumMap;
  private Map<Operation, Long> failPointNumMap;
  private static final String RESULT_ITEM = "%-20s";
  private static final String LATENCY_ITEM = "%-12s";


  public Measurement() {
    operationLatencies = new EnumMap<>(Operation.class);
    for (Operation operation : Operation.values()) {
      operationLatencies.put(operation, new ArrayList<>());
    }
    okOperationNumMap = new EnumMap<>(Operation.class);
    for (Operation operation : Operation.values()) {
      okOperationNumMap.put(operation, 0L);
    }
    failOperationNumMap = new EnumMap<>(Operation.class);
    for (Operation operation : Operation.values()) {
      failOperationNumMap.put(operation, 0L);
    }
    okPointNumMap = new EnumMap<>(Operation.class);
    for (Operation operation : Operation.values()) {
      okPointNumMap.put(operation, 0L);
    }
    failPointNumMap = new EnumMap<>(Operation.class);
    for (Operation operation : Operation.values()) {
      failPointNumMap.put(operation, 0L);
    }
    operationLatencySums = new EnumMap<>(Operation.class);
    for (Operation operation : Operation.values()) {
      operationLatencySums.put(operation, 0D);
    }
    getOperationLatencySumsList = new EnumMap<>(Operation.class);
    for (Operation operation : Operation.values()) {
      getOperationLatencySumsList.put(operation, new ArrayList<>());
    }
  }

  private Map<Operation, Double> getOperationLatencySums() {
    for (Operation operation : Operation.values()) {
      operationLatencySums.put(operation, getDoubleListSum(operationLatencies.get(operation)));
    }
    return operationLatencySums;
  }

  public long getOkOperationNum(Operation operation) {
    return okOperationNumMap.get(operation);
  }

  public long getFailOperationNum(Operation operation) {
    return failOperationNumMap.get(operation);
  }

  public long getOkPointNum(Operation operation) {
    return okPointNumMap.get(operation);
  }

  public long getFailPointNum(Operation operation) {
    return failPointNumMap.get(operation);
  }

  public void addOperationLatency(Operation op, double latency) {
    operationLatencies.get(op).add(latency);
  }

  public void addOkPointNum(Operation operation, int pointNum) {
    okPointNumMap.put(operation, okPointNumMap.get(operation) + pointNum);
  }

  public void addFailPointNum(Operation operation, int pointNum) {
    failPointNumMap.put(operation, failPointNumMap.get(operation) + pointNum);
  }

  public void addOkOperationNum(Operation operation) {
    okOperationNumMap.put(operation, okOperationNumMap.get(operation) + 1);
  }

  public void addFailOperationNum(Operation operation) {
    failOperationNumMap.put(operation, failOperationNumMap.get(operation) + 1);
  }

  private Map<Operation, List<Double>> getOperationLatencies() {
    return operationLatencies;
  }

  public void setOperationLatencies(
      Map<Operation, List<Double>> operationLatencies) {
    this.operationLatencies = operationLatencies;
  }

  public double getCreateSchemaTime() {
    return createSchemaTime;
  }

  public void setCreateSchemaTime(double createSchemaTime) {
    this.createSchemaTime = createSchemaTime;
  }

  public double getElapseTime() {
    return elapseTime;
  }

  public void setElapseTime(double elapseTime) {
    this.elapseTime = elapseTime;
  }

  /**
   * users need to call calculateMetrics() after calling mergeMeasurement() to update metrics.
   *
   * @param m measurement to be merged
   */
  public void mergeMeasurement(Measurement m) {
    for (Operation operation : Operation.values()) {
      operationLatencies.get(operation).addAll(m.getOperationLatencies().get(operation));
      getOperationLatencySumsList.get(operation).add(m.getOperationLatencySums().get(operation));
      okOperationNumMap
          .put(operation, okOperationNumMap.get(operation) + m.getOkOperationNum(operation));
      failOperationNumMap
          .put(operation, failOperationNumMap.get(operation) + m.getFailOperationNum(operation));
      okPointNumMap.put(operation, okPointNumMap.get(operation) + m.getOkPointNum(operation));
      failPointNumMap.put(operation, failPointNumMap.get(operation) + m.getFailPointNum(operation));
    }
  }

  public void calculateMetrics() {
    for (Operation operation : Operation.values()) {
      List<Double> latencyList = operationLatencies.get(operation);
      if (!latencyList.isEmpty()) {
        int totalOps = latencyList.size();
        double sumLatency = 0;
        for (double latency : latencyList) {
          sumLatency += latency;
        }
        double avgLatency = 0;
        avgLatency = sumLatency / totalOps;
        double maxThreadLatencySum = getDoubleListMax(getOperationLatencySumsList.get(operation));
        Metric.MAX_THREAD_LATENCY_SUM.getTypeValueMap().put(operation, maxThreadLatencySum);
        Metric.AVG_LATENCY.getTypeValueMap().put(operation, avgLatency);
        latencyList.sort(new DoubleComparator());
        Metric.MIN_LATENCY.getTypeValueMap().put(operation, latencyList.get(0));
        Metric.MAX_LATENCY.getTypeValueMap().put(operation, latencyList.get(totalOps - 1));
        Metric.P10_LATENCY.getTypeValueMap()
            .put(operation, latencyList.get((int) (totalOps * 0.10)));
        Metric.P25_LATENCY.getTypeValueMap()
            .put(operation, latencyList.get((int) (totalOps * 0.25)));
        Metric.MEDIAN_LATENCY.getTypeValueMap()
            .put(operation, latencyList.get((int) (totalOps * 0.50)));
        Metric.P75_LATENCY.getTypeValueMap()
            .put(operation, latencyList.get((int) (totalOps * 0.75)));
        Metric.P90_LATENCY.getTypeValueMap()
            .put(operation, latencyList.get((int) (totalOps * 0.90)));
        Metric.P95_LATENCY.getTypeValueMap()
            .put(operation, latencyList.get((int) (totalOps * 0.95)));
        Metric.P99_LATENCY.getTypeValueMap()
            .put(operation, latencyList.get((int) (totalOps * 0.99)));
        double midAvgLatency = 0;
        double midSum = 0;
        int midCount = 0;
        for (int i = (int) (totalOps * MID_AVG_RANGE[0]); i < (int) (totalOps * MID_AVG_RANGE[1]);
            i++) {
          midSum += latencyList.get(i);
          midCount++;
        }
        if (midCount != 0) {
          midAvgLatency = midSum / midCount;
        } else {
          LOGGER
              .error("Can not calculate mid-average latency because mid-operation number is zero.");
        }
        Metric.MID_AVG_LATENCY.getTypeValueMap().put(operation, midAvgLatency);
      }
    }
  }

  public void showMeasurements() {
    PersistenceFactory persistenceFactory = new PersistenceFactory();
    ITestDataPersistence recorder = persistenceFactory.getPersistence();
    System.out.println(Thread.currentThread().getName() + " measurements:");
    System.out.println("Create schema cost " + String.format("%.2f", createSchemaTime) + " second");
    System.out.println("Test elapsed time (not include schema creation): " + String.format("%.2f", elapseTime) + " second");
    recorder.saveResult("total", TotalResult.CREATE_SCHEMA_TIME.getName(), "" + createSchemaTime);
    recorder.saveResult("total", TotalResult.ELAPSED_TIME.getName(), "" + elapseTime);

    System.out.println(
        "----------------------------------------------------------Result Matrix----------------------------------------------------------");
    StringBuilder format = new StringBuilder();
    for (int i = 0; i < 6; i++) {
      format.append(RESULT_ITEM);
    }
    format.append("\n");
    System.out.printf(format.toString(), "Operation", "okOperation", "okPoint", "failOperation", "failPoint", "throughput(point/s)");
    for (Operation operation : Operation.values()) {
      String throughput = String.format("%.2f", okPointNumMap.get(operation) / elapseTime);
      System.out.printf(format.toString(), operation.getName(), okOperationNumMap.get(operation), okPointNumMap.get(operation),
          failOperationNumMap.get(operation), failPointNumMap.get(operation), throughput);

      recorder.saveResult(operation.toString(), TotalOperationResult.OK_OPERATION_NUM.getName(), "" + okOperationNumMap.get(operation));
      recorder.saveResult(operation.toString(), TotalOperationResult.OK_POINT_NUM.getName(), "" + okPointNumMap.get(operation));
      recorder.saveResult(operation.toString(), TotalOperationResult.FAIL_OPERATION_NUM.getName(), "" + failOperationNumMap.get(operation));
      recorder.saveResult(operation.toString(), TotalOperationResult.FAIL_POINT_NUM.getName(), "" + failPointNumMap.get(operation));
      recorder.saveResult(operation.toString(), TotalOperationResult.THROUGHPUT.getName(), throughput);
    }
    System.out.println(
        "---------------------------------------------------------------------------------------------------------------------------------");

    recorder.close();
  }

  public void showConfigs() {
    System.out.println("----------------------Main Configurations----------------------");
    System.out.println("DB_SWITCH: " + config.DB_SWITCH);
    System.out.println("OPERATION_PROPORTION: " + config.OPERATION_PROPORTION);
    System.out.println("IS_CLIENT_BIND: " + config.IS_CLIENT_BIND);
    System.out.println("CLIENT_NUMBER: " + config.CLIENT_NUMBER);
    System.out.println("GROUP_NUMBER: " + config.GROUP_NUMBER);
    System.out.println("DEVICE_NUMBER: " + config.DEVICE_NUMBER);
    System.out.println("SENSOR_NUMBER: " + config.SENSOR_NUMBER);
    System.out.println("BATCH_SIZE: " + config.BATCH_SIZE);
    System.out.println("LOOP: " + config.LOOP);
    System.out.println("POINT_STEP: "+ config.POINT_STEP);
    System.out.println("QUERY_INTERVAL: " + config.QUERY_INTERVAL);
    System.out.println("IS_OVERFLOW: " + config.IS_OVERFLOW);
    System.out.println("OVERFLOW_MODE: " + config.OVERFLOW_MODE);
    System.out.println("OVERFLOW_RATIO: " + config.OVERFLOW_RATIO);
    System.out.println("---------------------------------------------------------------");
  }

  public void showMetrics() {
    PersistenceFactory persistenceFactory = new PersistenceFactory();
    ITestDataPersistence recorder = persistenceFactory.getPersistence();
    System.out.println(
        "--------------------------------------------------------------------------Latency (ms) Matrix--------------------------------------------------------------------------");
    System.out.printf(RESULT_ITEM, "Operation");
    for (Metric metric : Metric.values()) {
      System.out.printf(LATENCY_ITEM, metric.name);
    }
    System.out.println();
    for (Operation operation : Operation.values()) {
      System.out.printf(RESULT_ITEM, operation.getName());
      for (Metric metric : Metric.values()) {
        String metricResult = String.format("%.2f", metric.typeValueMap.get(operation));
        System.out.printf(LATENCY_ITEM, metricResult);
        recorder.saveResult(operation.toString(), metric.name, metricResult);
      }
      System.out.println();
    }
    System.out.println(
        "-----------------------------------------------------------------------------------------------------------------------------------------------------------------------");
    recorder.close();
  }

  class DoubleComparator implements Comparator<Double> {

    @Override
    public int compare(Double a, Double b) {
      if (a < b) {
        return -1;
      } else if (Objects.equals(a, b)) {
        return 0;
      } else {
        return 1;
      }
    }
  }

  private double getDoubleListSum(List<Double> list) {
    double sum = 0;
    for (double item : list) {
      sum += item;
    }
    return sum;
  }

  private double getDoubleListMax(List<Double> list) {
    double max = 0;
    for (double item : list) {
      max = Math.max(max, item);
    }
    return max;
  }

}