package org.example.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import org.example.dao.OrderDao;
import org.example.model.Order;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class OrderQueryTool {
    private final OrderDao orderDao;
    private final RedisTemplate<String, Object> redisTemplate;
    private final Cache<String, Object> orderCache;
    private final Cache<String, Object> userOrdersCache;
    private final ObjectMapper objectMapper;

    public OrderQueryTool(OrderDao orderDao, RedisTemplate<String, Object> redisTemplate,
                          @Qualifier("orderCache") Cache<String, Object> orderCache,
                          @Qualifier("userOrdersCache") Cache<String, Object> userOrdersCache,
                          ObjectMapper objectMapper) {
        this.orderDao = orderDao;
        this.redisTemplate = redisTemplate;
        this.orderCache = orderCache;
        this.userOrdersCache = userOrdersCache;
        this.objectMapper = objectMapper;
    }

    @Tool(description = "查询指定用户的全部订单，按本地缓存、Redis、MySQL三级顺序读取")
    public String queryUserOrders(@ToolParam(description = "用户ID") String userId) {
        String key = "user_orders:" + userId;
        Object local = userOrdersCache.getIfPresent(key);
        if (local != null) return response(castOrders(local), "L1 Caffeine缓存");

        try {
            Object remote = redisTemplate.opsForValue().get(key);
            if (remote instanceof List<?> list) {
                List<Order> orders = castOrders(list);
                userOrdersCache.put(key, orders);
                return response(orders, "L2 Redis缓存");
            }
        } catch (Exception ignored) {
            // Redis 不可用时降级到 MySQL。
        }

        List<Order> orders = orderDao.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .orderByDesc(Order::getCreatedAt));
        userOrdersCache.put(key, orders);
        try { redisTemplate.opsForValue().set(key, orders, 5, TimeUnit.MINUTES); } catch (Exception ignored) { }
        return response(orders, "MySQL数据库");
    }

    @Tool(description = "查询指定订单详情")
    public String queryOrderDetail(@ToolParam(description = "订单ID") String orderId) {
        String key = "order:" + orderId;
        Object local = orderCache.getIfPresent(key);
        if (local instanceof Order order) return response(order, "L1 Caffeine缓存");
        try {
            Object remote = redisTemplate.opsForValue().get(key);
            if (remote instanceof Order order) {
                orderCache.put(key, order);
                return response(order, "L2 Redis缓存");
            }
        } catch (Exception ignored) { }

        Order order = orderDao.selectById(orderId);
        if (order == null) return "未找到订单: " + orderId;
        orderCache.put(key, order);
        try { redisTemplate.opsForValue().set(key, order, 5, TimeUnit.MINUTES); } catch (Exception ignored) { }
        return response(order, "MySQL数据库");
    }

    private String response(Object value, String source) {
        try {
            return objectMapper.writeValueAsString(Collections.singletonMap("source_" + source, value));
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Order> castOrders(Object value) {
        return (List<Order>) value;
    }
}
