package gwtupload.server;

import java.util.Date;

import javax.servlet.http.HttpServletRequest;

import com.efounder.buffer.SerializeUtil;
import com.efounder.buffer.redis.RedisCachedManager;

public class RedisUploadListener extends AbstractUploadListener {
    
    private static final long serialVersionUID = 1L;
    
    private static RedisCachedManager uploadRedisCached = RedisCachedManager.getDefault();
    private static String PRI = "GWT_UPLOAD_LISTENER:";
    public static String UPLOAD_ID = "gwtupload_id";
    
    private String uuid = null;
    
    public static RedisUploadListener current(String uuid) {
        byte[] value = uploadRedisCached.get((PRI + uuid).getBytes());
        RedisUploadListener listener = (RedisUploadListener) SerializeUtil.unserialize(value);
        logger.debug(className + " " + uuid + " get " + listener);
        return listener;
    }
    
    public RedisUploadListener(int sleepMilliseconds, long requestSize) {
        super(sleepMilliseconds, requestSize);
        HttpServletRequest request = UploadServlet.getThreadLocalRequest();
        uuid = request.getParameter(UPLOAD_ID);
    }
    
    public void remove() {
        uploadRedisCached.del((PRI + uuid).getBytes());
        logger.debug(className + " " + uuid + " Remove " + this.toString());
    }
    
    public void save() {
        if ( uuid == null || "".equals(uuid.trim()) ) {
            HttpServletRequest request = UploadServlet.getThreadLocalRequest();
            uuid = request.getParameter(UPLOAD_ID);
        }
        saved = new Date();
        uploadRedisCached.set((PRI + uuid).getBytes(), SerializeUtil.serialize(this), 600);
        logger.debug(className + " " + uuid + " Save " + this.toString());
    }
}
