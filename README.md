# 集群/分布式环境Session的几种策略 <br>

项目结构介绍：<br/>
* server-session, server-session-2,为springboot项目粘性session测试.
* app-session 为web服务项目需要依赖Tomcat启动。应用服务器间的session复制共享测试。
* server-session-redis, server-session-redis-2为springboot项目基于cache DB缓存的session共享测试。
* server-session-war 为springboot打war包的例子。
* config目录为windows版本 nginx, redis, tomcat集群配置文件。
* file目录为架构图。

本文主要参考：http://blog.csdn.net/woaigaolaoshi/article/details/50902010

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

可参考config内的nginx.conf配置：<br/>

    upstream load_balance_server {
        server 127.0.0.1:8001;
        server 127.0.0.1:9001;
        ip_hash;
    }
    server {
        listen       100;    		
        #定义使用www.xx.com访问
        #server_name www.helloworld.com;    		
        server_name  localhost;

        location / {
            root   html;
            index  index.html index.htm;
            #请求转向load_balance_server 定义的服务器列表
            proxy_pass http://load_balance_server ;
            
            #以下是一些反向代理的配置(可选择性配置)
            proxy_redirect off;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr; 
        }
    }	 

依次启动server-session, server-session-2项目，然后启动nginx进行测试。
### 2、应用服务器间的session复制共享
<img src="https://github.com/L316476844/distributed-session/blob/master/file/s2.png" alt="">

* 原理：任何一个服务器上的session发生改变（增删改），该节点会把这个 session的所有内容序列化，然后广播给所有其它节点，不管其他服务器需不需要session，以此来保证Session同步。
* 优点：可容错，各个服务器间session能够实时响应。
* 缺点：会对网络负荷造成一定压力，如果session量大的话可能会造成网络堵塞，拖慢服务器性能。
* 实现方式：本文以Tomcat8集群为例。
step1：复制app-session的war包解压到tomcat8 webapps ROOT目录下,tomcat复制两份,然后修改tomcat的server.xml文件。<br/>
tomcat1 server.xml 改动点:<br/>

        <!-- 同一台机器上需要保证shutdown  ajp等端口不一样 -->
        <Server port="8706" shutdown="SHUTDOWN">    
        <Connector port="9002" protocol="HTTP/1.1"
                       connectionTimeout="20000"
                       redirectPort="9746" />
                   
       <Connector port="8009" protocol="AJP/1.3" redirectPort="8443" />

        <!-- Engine的jvmRoute 参数名称与其它服务区分 -->
        <Engine name="Catalina" defaultHost="localhost" jvmRoute="tomcat1">

       <Cluster className="org.apache.catalina.ha.tcp.SimpleTcpCluster" channelSendOptions="8">
            <Manager className="org.apache.catalina.ha.session.DeltaManager"
            expireSessionsOnShutdown="false"
            notifyListenersOnReplication="true"/>
    
            <!--228.0.0.4 保留ip,用于广播-->
            <Channel className="org.apache.catalina.tribes.group.GroupChannel">
                <Membership className="org.apache.catalina.tribes.membership.McastService"
                    address="228.0.0.4"
                    port="45564"
                    frequency="500"
                    dropTime="3000"/> 
                
                <!-- port 如果是在同一台机器上的两个tomcat做负载，则此端口则不能重复-->
                <Receiver className="org.apache.catalina.tribes.transport.nio.NioReceiver"
                    address="127.0.0.1"
                    port="4008" 
                    autoBind="100"
                    selectorTimeout="5000"
                    maxThreads="6"/>
                
                <Sender className="org.apache.catalina.tribes.transport.ReplicationTransmitter">
                    <Transport className="org.apache.catalina.tribes.transport.nio.PooledParallelSender"/>
                </Sender>
                <Interceptor className="org.apache.catalina.tribes.group.interceptors.TcpFailureDetector"/>
                <Interceptor className="org.apache.catalina.tribes.group.interceptors.MessageDispatch15Interceptor"/>
            </Channel>
    
            <Valve className="org.apache.catalina.ha.tcp.ReplicationValve" filter=""/>
            <Valve className="org.apache.catalina.ha.session.JvmRouteBinderValve"/>
    
            <Deployer className="org.apache.catalina.ha.deploy.FarmWarDeployer"
                tempDir="/tmp/war-temp/"
                deployDir="/tmp/war-deploy/"
                watchDir="/tmp/war-listen/"
                watchEnabled="false"/>
            <ClusterListener className="org.apache.catalina.ha.session.ClusterSessionListener"/>
        </Cluster>

tomcat2 server.xml改动点：<br/>

    <Server port="8006" shutdown="SHUTDOWN">
    <Connector port="8002" protocol="HTTP/1.1"
                   connectionTimeout="20000"
                   redirectPort="9446" />
    <Connector port="8019" protocol="AJP/1.3" redirectPort="8444" /> 
     <!-- Engine的jvmRoute 参数名称与其它服务区分 -->
    <Engine name="Catalina" defaultHost="localhost" jvmRoute="tomcat2">
     
    <Cluster className="org.apache.catalina.ha.tcp.SimpleTcpCluster" channelSendOptions="8">
        <Manager className="org.apache.catalina.ha.session.DeltaManager"
        expireSessionsOnShutdown="false"
        notifyListenersOnReplication="true"/>

        <!--228.0.0.4 保留ip,用于广播-->
        <Channel className="org.apache.catalina.tribes.group.GroupChannel">
            <Membership className="org.apache.catalina.tribes.membership.McastService"
                address="228.0.0.4"
                port="45564"
                frequency="500"
                dropTime="3000"/> 
            
            <!-- port 如果是在同一台机器上的两个tomcat做负载，则此端口则不能重复-->
            <Receiver className="org.apache.catalina.tribes.transport.nio.NioReceiver"
                address="127.0.0.1"
                port="4002" 
                autoBind="100"
                selectorTimeout="5000"
                maxThreads="6"/>
            
            <Sender className="org.apache.catalina.tribes.transport.ReplicationTransmitter">
                <Transport className="org.apache.catalina.tribes.transport.nio.PooledParallelSender"/>
            </Sender>
            <Interceptor className="org.apache.catalina.tribes.group.interceptors.TcpFailureDetector"/>
            <Interceptor className="org.apache.catalina.tribes.group.interceptors.MessageDispatch15Interceptor"/>
        </Channel>

        <Valve className="org.apache.catalina.ha.tcp.ReplicationValve" filter=""/>
        <Valve className="org.apache.catalina.ha.session.JvmRouteBinderValve"/>

        <Deployer className="org.apache.catalina.ha.deploy.FarmWarDeployer"
            tempDir="/tmp/war-temp/"
            deployDir="/tmp/war-deploy/"
            watchDir="/tmp/war-listen/"
            watchEnabled="false"/>
        <ClusterListener className="org.apache.catalina.ha.session.ClusterSessionListener"/>
    </Cluster>            

step2: **应用内app-session（web.xml)配置必须添加`<distributable/>`目前springboot中没找到替代方式。** <br/>

step3: 启动tomcat1,tomcat2 启动nginx 测试 <br/>
    		
### 3、基于cache DB缓存的session共享
<img src="https://github.com/L316476844/distributed-session/blob/master/file/s3.png" alt="">

* 原理：session持久化到缓存或者db中。
* 优点：gemfire,memcache或则redis本身就是一个分布式缓存，便于扩展。网络开销较小，IO开销也非常小，性能也更好。服务器出现问题，session不会丢失。
* 缺点：假如突然涌来大量用户产生了很多数据把存储 session 的机器内存占满了redis会变的比较慢
* 实现方式：spring-session-data-redis

server-session-redis, server-session-redis-2为springboot项目已经集成redis session。<br/>
    
    <!-- pom添加 -->
    <dependency>
        <groupId>org.springframework.session</groupId>
        <artifactId>spring-session-data-redis</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    
    <!-- 添加配置类 -->
    @Configuration
    @EnableRedisHttpSession
    public class RedisSessionConfig {
    }
    
    <!-- application.properties 添加配置-->
    spring.redis.host=localhost
    spring.redis.port=6379

依次启动redis服务, server-session-redis, server-session-redis-2, ngix服务测试。 <br/>


### 4、spring-session-data-redis源码解读

    首先切入点在`@EnableRedisHttpSession`注解类上,查看源码需要引入`@Import(RedisHttpSessionConfiguration.class)`类。
    @Configuration
    @EnableScheduling
    public class RedisHttpSessionConfiguration extends SpringHttpSessionConfiguration
    		implements EmbeddedValueResolverAware, ImportAware {
    此类继承至SpringHttpSessionConfiguration 同时此类也声明了redis的操作模板
    @Bean
    public RedisTemplate<Object, Object> sessionRedisTemplate(	
    redis session操作库
    @Bean
    public RedisOperationsSessionRepository sessionRepository(
    
    SpringHttpSessionConfiguration类中定义了SessionRepositoryFilter过滤器来操作session该过滤器的order是最后执行
    @Bean
    public <S extends ExpiringSession> SessionRepositoryFilter<? extends ExpiringSession> springSessionRepositoryFilter(
    SessionRepositoryFilter类中包含找到FilterChain类中的doFilter方法发现对HttpRequest和HttpResponse进行了包装
    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        request.setAttribute(SESSION_REPOSITORY_ATTR, this.sessionRepository);

        SessionRepositoryRequestWrapper wrappedRequest = new SessionRepositoryRequestWrapper(
                request, response, this.servletContext);
        SessionRepositoryResponseWrapper wrappedResponse = new SessionRepositoryResponseWrapper(
                wrappedRequest, response);

        HttpServletRequest strategyRequest = this.httpSessionStrategy
                .wrapRequest(wrappedRequest, wrappedResponse);
        HttpServletResponse strategyResponse = this.httpSessionStrategy
                .wrapResponse(wrappedRequest, wrappedResponse);

        try {
            filterChain.doFilter(strategyRequest, strategyResponse);
        }
        finally {
            wrappedRequest.commitSession();
        }
    }
    从SessionRepositoryRequestWrapper请求的包装类中可以看到session的创建和保存及获取
    private void commitSession() {
        HttpSessionWrapper wrappedSession = getCurrentSession();
        if (wrappedSession == null) {
            if (isInvalidateClientSession()) {
                SessionRepositoryFilter.this.httpSessionStrategy
                        .onInvalidateSession(this, this.response);
            }
        }
        else {
            S session = wrappedSession.getSession();
            SessionRepositoryFilter.this.sessionRepository.save(session);
            if (!isRequestedSessionIdValid()
                    || !session.getId().equals(getRequestedSessionId())) {
                SessionRepositoryFilter.this.httpSessionStrategy.onNewSession(session,
                        this, this.response);
            }
        }
    }
    此刻我们基本已经了解spring session生成的过程，但是session是有有效期的。接下来我们查看一下session的定时任务.
    前面我们说到RedisHttpSessionConfiguration内声明了RedisOperationsSessionRepository类实例对象，该对象内
    @Scheduled(cron = "${spring.session.cleanup.cron.expression:0 * * * * *}")
    public void cleanupExpiredSessions() {
        this.expirationPolicy.cleanExpiredSessions();
    }
    开启了定时任务来清理过期的session。
    
### 5、springboot打war包方式 - server-session-war

* pom打包方式修改为war包
    `<packaging>war</packaging>`
* pom中去掉spring-boot-starter-web包
* pom中引入spring-boot-starter-tomcat具体参考server-session-war  pom文件
* SpringBootApplication启动类继承SpringBootServletInitializer类且实现configure接口

        @SpringBootApplication
        public class ServerSessionApplication extends SpringBootServletInitializer {
        
            @Override
            protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
                
                return application.sources(ServerSessionApplication.class);
            }
        
            public static void main(String[] args) {
                SpringApplication.run(ServerSessionApplication.class, args);
        
            }
        }
        

