# comment-gateway 网关

评论系统的**统一入口**。所有外部请求只认 9201，由网关转发到后端服务，前端不需要知道后端有几个服务、部署在哪。

## 在整体架构中的位置

```
前端 / 客户端
      │
      ▼
comment-gateway (9201)          ← 本项目
      │  lb://feidian-comment（按服务名负载均衡）
      ▼
comment-service (8081) ──Feign──→ user-service (8082)
```

## 技术栈

| 组件 | 版本 |
|---|---|
| Java | 11 |
| Spring Boot | 2.3.0.RELEASE |
| Spring Cloud | Hoxton.SR9 |
| Spring Cloud Alibaba | 2.2.6.RELEASE |
| Spring Cloud Gateway | 随 Hoxton |
| Nacos | 注册中心 |

> Spring Cloud Gateway 基于 **WebFlux（响应式）**，不能和 `spring-boot-starter-web` 共存，否则启动直接报错。

## 路由规则

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: comment_route
          uri: lb://feidian-comment      # lb:// 表示从 Nacos 取实例列表做负载均衡
          predicates:
            - Path=/api/**                # 只匹配 /api 开头的请求
          filters:
            - StripPrefix=1               # 转发前去掉第一段 /api
```

三个概念对应网关的三件事：

| 概念 | 作用 | 本项目的配置 |
|---|---|---|
| Route（路由） | 请求转发到哪 | `uri: lb://feidian-comment` |
| Predicate（断言） | 什么样的请求匹配这条路由 | `Path=/api/**` |
| Filter（过滤器） | 转发前后做什么 | `StripPrefix=1` + 自定义耗时统计 |

所以：

```
http://localhost:9201/api/comment/findByRefId?refId=article-001
                       └─┬─┘
                    被 StripPrefix 剥掉
                          ↓
http://localhost:8081/comment/findByRefId?refId=article-001
```

## 自定义过滤器

`RequestTimeFilter` 是一个 `GlobalFilter`，统计每个请求从进入到返回的耗时：

```java
@Override
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    exchange.getAttributes().put(START_TIME, System.currentTimeMillis());   // 进入时记时间
    return chain.filter(exchange).then(Mono.fromRunnable(() -> {            // 响应回来后计算差值
        log.info(path + " 请求耗时: " + (System.currentTimeMillis() - startTime) + "ms");
    }));
}
```

**为什么放在网关**：鉴权、日志、限流、耗时统计这些"每个请求都要做"的事，放在网关做一次就够了；放在每个服务里做，就是重复 N 遍。这也是网关存在的核心理由之一。

## 启动

前置：Nacos 已启动，且 `comment-service` 已注册。

```powershell
mvn -B spring-boot:run
```

验证：

```bash
# 通过网关访问（9201）
curl.exe "http://localhost:9201/api/comment/findByRefId?refId=article-001"

# 对比直连（8081），返回应完全一致
curl.exe "http://localhost:8081/comment/findByRefId?refId=article-001"
```

## 踩过的坑

| 现象 | 原因 |
|---|---|
| 启动报 `Spring MVC found on classpath` | 网关依赖了 `spring-boot-starter-web`，WebFlux 和 MVC 不能共存 |
| 404，路径少了一段或多了 `/api` | `StripPrefix` 没配或值不对；`StripPrefix=1` 剥掉第一段 |
| 503 `Unable to find instance for feidian-comment` | 目标服务没注册到 Nacos，或服务名写错（注意 `lb://` 后面是**注册名**，不是 IP） |

## 说明

- 网关是独立项目而不是 comment-service 的一个模块，原因是：它的生命周期、扩容策略、故障影响面都和业务服务不同（网关挂掉 = 全部不可用）。
- 学习用途项目，重点在网关的路由与过滤器机制。
