# 集群/分布式环境Session的几种策略 <br>

### 集群/分布式session产生的原因？ 
B/S交互下是通过http协议完成，Http是一个无状态协议。无状态是指，当浏览器发送请求给服务器的时候，服务器响应，<br/>
但是同一个浏览器再发送请求给服务器的时候，他会响应，但是他不知道你就是刚才那个浏览器，简单地说，就是服务器不会去记得你，所以是无状态协议。
同一个会话的连续两个请求互相不了解，也就是说当我们登录之后浏览商品下单购买时还要再次登录。为了解决这种情况引入了cookie和session。<br/>

Cookie是通过客户端保持状态的解决方案。从定义上来说，Cookie就是由服务器发给客户端的特殊信息，而这些信息以文本文件的方式存放在客户端，
然后客户端每次向服务器发送请求的时候都会带上这些特殊的信息。<br/>
Session就是一种保存上下文信息的机制，它是针对每一个用户的，变量的值保存在服务器端，通过SessionID来区分不同的客户,
Session是以cookie或URL重写为基础的，默认使用cookie来实现，系统会创造一个名为JSESSIONID的输出返回给客户端Cookie保存。<br/>

**强调一点：session和cookie是一一对应的关系。**
### 1、粘性session
<img src="https://github.com/L316476844/distributed-session/blob/master/file/s1.png" alt="">

+ 原理：粘性Session是指将用户锁定到某一个服务器上,用户第一次请求时，负载均衡器将用户的请求转发到了A服务器上,那么用户以后的每次请求都会转发到A服务器上。
+ 优点：简单，不需要对session做任何处理。
+ 缺点：缺乏容错性，如果当前访问的服务器发生故障，用户被转移到第二个服务器上时，他的session信息都将失效。
+ 实现方式：以Nginx为例，在upstream模块配置ip_hash属性即可实现粘性Session。


### 2、应用服务器间的session复制共享
<img src="https://github.com/L316476844/distributed-session/blob/master/file/s2.png" alt="">

### 3、基于cache DB缓存的session共享
<img src="https://github.com/L316476844/distributed-session/blob/master/file/s3.png" alt="">

