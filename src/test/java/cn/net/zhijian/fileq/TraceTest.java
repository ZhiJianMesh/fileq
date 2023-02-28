package cn.net.zhijian.fileq;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

import cn.net.zhijian.fileq.util.LogUtil;
/**
 * There is a great gap between map and array tracing.
 * But it is not a big problem, because they're all fast enough.
 * 
 * ConcurrentMap,time:4483,speed:35690385
 * Map,time:3166,speed:50536955
 * Arr,time:209,speed:765550239
 * @author Lgy
 *
 */
public class TraceTest extends TestBase {
    private static final int N = 20000000;
    private static Logger LOG = LogUtil.getInstance();

    @SuppressWarnings("unused")
    public static void main(String[] args) {
        int threadNum = Runtime.getRuntime().availableProcessors();
        Map<String, String> concurrentMap = new ConcurrentHashMap<>();
        Map<String, String> map = new HashMap<>();
        String[] arr = new String[10];
        for(int i = 0; i < 10; i++) {
            map.put("k" + i, "v" + i);
            concurrentMap.put("k" + i, "v" + i);
            arr[i] = "v" + i;
        }
        
        long time = performTest("trace_concurrent_map", threadNum, () -> {
            for(int i = 0; i < N; i++) {
                for(Map.Entry<String, String> e : concurrentMap.entrySet());
            }
        });
        LOG.debug("ConcurrentMap,time:{},speed:{}", time, (1000L * N * threadNum) / time);
        
        time = performTest("trace_map", threadNum, () -> {
            for(int i = 0; i < N; i++) {
                for(Map.Entry<String, String> e : map.entrySet());
            }
        });
        LOG.debug("Map,time:{},speed:{}", time, (1000L * N * threadNum) / time);
        
        time = performTest("trace_arr", threadNum, () -> {
            for(int i = 0; i < N; i++) {
                for(String s : arr);
            }
        });        
        LOG.debug("Arr,time:{},speed:{}", time, (1000L * N * threadNum) / time);
        
        System.exit(0);
    }
}
