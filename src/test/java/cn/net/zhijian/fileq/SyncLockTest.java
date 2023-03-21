package cn.net.zhijian.fileq;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
/**
 * TN=100000,N=10000
 * Sync use time:10196,speed:98077677
 * Lock use time:10189,speed:98145058
 * 
 * TN=1000000,N=1000
 * Sync use time:10406,speed:96098404
 * Lock use time:11663,speed:85741232
 * 
 * So there isn't big gap between them,
 * `synchronized` is very easy to use.
 * Lock is agile.
 * It is surprising that synchronized is a little better than lock.
 * @author Lgy
 *
 */
public class SyncLockTest {
    private static int TN = 1000000;
    private static int N = 1000;
    private static ExecutorService threadPool = Executors.newFixedThreadPool(16);
    private static ReentrantLock lock = new ReentrantLock();
    
    public static void main(String[] args) throws InterruptedException {
        int total = TN * N;
        long start = System.currentTimeMillis();
        CountDownLatch counter1 = new CountDownLatch(total);
        for(int i = 0; i < TN; i++) {
            threadPool.execute(() -> fun1(counter1));
        }
        counter1.await();
        long end = System.currentTimeMillis();
        long useTime = end - start;
        System.out.println("Sync use time:" + useTime + ",speed:" + (1000L * total / useTime));
        
        start = System.currentTimeMillis();
        CountDownLatch counter2 = new CountDownLatch(total);
        for(int i = 0; i < TN; i++) {
            threadPool.execute(() -> fun2(counter2));
        }
        counter2.await();
        end = System.currentTimeMillis();
        useTime = end - start;
        System.out.println("Lock use time:" + useTime + ",speed:" + (1000L * total / useTime));
        
        System.exit(0);
    }
    
    private static synchronized void fun1(CountDownLatch counter) {
        for(int i = 0; i < N; i++) {
            counter.countDown();
        }
    }
    
    private static void fun2(CountDownLatch counter) {
        lock.lock();
        try {
            for(int i = 0; i < N; i++) {
                counter.countDown();
            }
        } finally {
            lock.unlock();
        }
    }
}
