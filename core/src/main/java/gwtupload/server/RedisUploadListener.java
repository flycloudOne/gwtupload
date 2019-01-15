package gwtupload.server;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletRequest;

import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.MemoryUnit;
import org.ehcache.expiry.Duration;
import org.ehcache.expiry.Expirations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @Description:TODO
 * @author ZhangJQ
 * @time:2019年1月2日 下午8:28:15
 */
public class RedisUploadListener extends AbstractUploadListener {
    
    private static final Logger logger = LoggerFactory.getLogger(RedisUploadListener.class);
    
    private static final long serialVersionUID = 1L;
    
    private static String PRI = "GWT_UPLOAD_LISTENER:";
    public static String UPLOAD_ID = "gwtupload_id";
    private static String CACHE_TYPE = "RedisUploadListener";
    private String uuid = null;
    public static CacheManager cacheManager = null;
    private static Cache<String, RedisUploadListener> sysCache = null;

    static {
        if (cacheManager == null) {
            // 构建缓存管理器
            cacheManager = CacheManagerBuilder.newCacheManagerBuilder().build();
            // 初始化对象
            cacheManager.init();
        }
        sysCache = cacheManager.getCache(CACHE_TYPE, String.class, RedisUploadListener.class);

        if (sysCache == null) { // 所有的信息都存在堆内
            ResourcePoolsBuilder resourcePoolsBuilder = ResourcePoolsBuilder.newResourcePoolsBuilder()
                    .offheap(2, MemoryUnit.GB);
            // 存储对象类型，过期时间等配置定义
            CacheConfiguration<String, RedisUploadListener> cacheConfiguration = CacheConfigurationBuilder
                    .newCacheConfigurationBuilder(String.class, RedisUploadListener.class, resourcePoolsBuilder)
                    .withExpiry(Expirations.timeToLiveExpiration(Duration.of(10, TimeUnit.MINUTES)))
                    .build();
            cacheManager.createCache(CACHE_TYPE, cacheConfiguration); 
          
            sysCache = cacheManager.getCache(CACHE_TYPE, String.class, RedisUploadListener.class);
        }
    }
    
    public static RedisUploadListener current(String uuid) {
        RedisUploadListener listener = sysCache.get(PRI + uuid);
        logger.debug(className + " " + uuid + " get " + listener);
        return listener;
    }
    
    public RedisUploadListener(int sleepMilliseconds, long requestSize) {
        super(sleepMilliseconds, requestSize);
        HttpServletRequest request = UploadServlet.getThreadLocalRequest();
        uuid = request.getParameter(UPLOAD_ID);
      }
    
    public void remove() {
        sysCache.remove(PRI + uuid);
        logger.debug(className + " " + uuid + " Remove " + this.toString());
    }
    
    public void save() {
        if ( uuid == null || "".equals(uuid.trim()) ) {
            HttpServletRequest request = UploadServlet.getThreadLocalRequest();
            uuid = request.getParameter(UPLOAD_ID);
        }
        saved = new Date();
        sysCache.put(PRI + uuid, this);
        logger.debug(className + " " + uuid + " Save " + this.toString());
    }
}
