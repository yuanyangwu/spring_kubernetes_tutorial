# RabbitMQ Spring Application

Wikipedia says, "Message queues provide an asynchronous communications protocol, meaning that the sender and receiver of the message do not need to interact with the message queue at the same time. Messages placed onto the queue are stored until the recipient retrieves them."

RabbitMQ is an open source message broker software (sometimes called message-oriented middleware) that originally implemented the Advanced Message Queuing Protocol (AMQP).

With message queue, we can implement task queue.

- Sender enqueues tasks to the queue.
- Receivers dequeue tasks from the queue.
- Message queue distributes tasks among receivers.
- If receiver handles task by error, it rejects task. Then message queue will distributes the task to same or different receiver.

![RabbitMQ Task Queue](images/rabbitmq-task-queue.png)

## Play RabbitMQ under Ubuntu

Let us play with RabbitMQ under Ubuntu 16.04 first.

Install RabbitMQ and AMQP tool

```bash
sudo apt-get install -y rabbitmq-server curl ca-certificates amqp-tools python dnsutils
sudo systemctl start rabbitmq-server.service
sudo systemctl enable rabbitmq-server.service
```

Change RabbitMQ listening IP to make RabbitMQ serve requests from other hosts.

```bash
sudo vi /etc/rabbitmq/rabbitmq-env.conf
# NODE_IP_ADDRESS=0.0.0.0
# NODE_PORT=5672

sudo systemctl stop rabbitmq-server.service
sudo systemctl start rabbitmq-server.service
```

Create user "admin" with password "secret"

```bash
sudo rabbitmqctl add_user admin secret
sudo rabbitmqctl set_user_tags admin administrator
sudo rabbitmqctl set_permissions -p / admin ".*" ".*" ".*"
```

Create, enqueue and dequeue a queue

```bash
# RabbitMQ credential and URL
vagrant@k8smaster:~$ export BROKER_URL=amqp://guest:guest@localhost:5672

# Create a queue
vagrant@k8smaster:~$ /usr/bin/amqp-declare-queue --url=$BROKER_URL -q foo -d
foo

# Send "A" to the queue
vagrant@k8smaster:~$ /usr/bin/amqp-publish --url=$BROKER_URL -r foo -p -b A

# Receive "A" from the queue
vagrant@k8smaster:~$ /usr/bin/amqp-consume --url=$BROKER_URL -q foo -c 1 cat && echo
A
vagrant@k8smaster:~$
```

## Play RabbitMQ with Spring

Spring has smooth RabbitMQ support. Here is our application design.

- Implement both sender and receiver with same project. Use spring profile to launch application in either "sender" or "receiver" mode
- Sender sends a message from queue "hello" every 1s
- Receiver receives messages from queue "hello"
  - It spends 0.1s proceeding a message
  - It throws exception for 20% messages. It is to verify that system can retry when receive goes error.
- RabbitMQ credential, IP and port are configured via environment variables.

### Main application

Notes

- 1: To enable ```@Scheduled``` in MySender.send
- 2: Queue "hello"
- 3: Use ```@Profile``` to control whether to create sender and/or receiver related bean.

```java
@SpringBootApplication
@EnableScheduling // 1
public class RabbitMQApplication {
  public static void main(String[] args) {
    SpringApplication.run(RabbitMQApplication.class, args);
  }

  @Bean
  public Queue hello() {  // 2
    return new Queue("hello");
  }

  @Profile("sender")  // 3
  @Bean
  public MySender sender() {
    return new MySender();
  }

  @Profile("receiver") // 3
  @Bean
  public MyReceiver receiver1() {
    return new MyReceiver();
  }
}
```

### Runner

To make application run endlessly.

```java
@Component
public class MyRunner implements CommandLineRunner {
  @Override
  public void run(String... args) throws Exception {
    while (true) {
      Thread.sleep(1000);
    }
  }
}
```

### Sender

Notes

- 1: Spring RabbitMQ template defined in spring-boot-starter-amqp, used to access RabbitMQ
- 2: RabbitMQ queue
- 3: Called every 1s. Enabled by ```@EnableScheduling``` in main application.
- 4: Send message to queue

```java
@Log
public class MySender {
  @Autowired
  private RabbitTemplate template; // 1

  @Autowired
  private Queue queue; // 2

  int dots = 0;
  int count = 0;

  @Scheduled(fixedDelay = 1000, initialDelay = 500) // 3
  public void send() {
    StringBuilder builder = new StringBuilder("Hello");
    if (dots++ == 3) {
      dots = 1;
    }

    for (int i = 0; i < dots; i++) {
      builder.append('.');
    }

    builder.append(Integer.toString(++count));
    String message = builder.toString();
    template.convertAndSend(queue.getName(), message); // 4
    log.info("[x] Sent '" + message + "'");
  }
}
```

### Receiver

Notes

- 1: Listen RabbitMQ queue "hello"
- 2: Called if RabbitMQ has new message to receive
- 3: It spends 0.1s proceeding a message
- 4: It throws exception for 20% of message

Spring AMQP by default takes a conservative approach to message acknowledgement. If the listener throws an exception, the container calls ```channel.basicReject(deliveryTag, requeue)``` to enqueue message back to message queue.

```java
@Log
@RabbitListener(queues = "hello") // 1
public class MyReceiver {
  @RabbitHandler // 2
  public void receive(String in) throws Exception {
    Thread.sleep(100); // 3
    if (Math.random() < 0.2) { // 4
      log.severe("[x] Received Exception " + in);
      throw new Exception("Exception " + in);
    }
    log.info("[x] Received '" + in + "'");
  }
}
```

### Configuration (application.yml)

Notes

- 1: Default spring active profiles. It can be overridden by environment variables or command argument.
- 2: Default RabbitMQ server credential, IP and password.
- 3: Default logging settings. Suppress ConditionalRejectingErrorHandler warning to remove call stack by receiver exception.
- 4: Logging output pattern. Add host name to make it easy to recognize log related host or docker instance.

```yaml
spring:
  profiles:
    active: # 1
    - receiver
    #- sender
  rabbitmq: # 2
    host: 127.0.0.1
    port: 5672
    username: admin
    password: secret

logging:
  level: # 3
    root: INFO
    org.springframework.amqp.rabbit.listener.ConditionalRejectingErrorHandler: ERROR
  pattern: # 4
    # add "${HOSTNAME}" to get log related host/docker
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} %5p ${HOSTNAME} ${PID:- } --- [%15.15t] %-40.40logger{39} : %m%n"
```

### Environment variable summary

Here are related environment variables, which will be used by future k8s/docker work.

- SPRING_PROFILES_ACTIVE, active profile. "sender" for sender mode. "receiver" for receiver mode.
- SPRING_RABBITMQ_HOST, RabbitMQ server IP
- SPRING_RABBITMQ_PORT, RabbitMQ server port
- SPRING_RABBITMQ_USERNAME, RabbitMQ server user name
- SPRING_RABBITMQ_PASSWORD, RabbitMQ server password

### Test

Build package

```bash
mvn -DskipTests package
```

You can launch application with different spring profiles in multiple ways.

Option 1: command argument

```bash
java -jar target\rabbitmq-app-0.0.1-SNAPSHOT.jar --spring.profiles.active=sender
java -jar target\rabbitmq-app-0.0.1-SNAPSHOT.jar --spring.profiles.active=receiver
```

Option 2: environment variable

```bash
SPRING_PROFILES_ACTIVE=sender java -jar target\rabbitmq-app-0.0.1-SNAPSHOT.jar
SPRING_PROFILES_ACTIVE=receiver java -jar target\rabbitmq-app-0.0.1-SNAPSHOT.jar
```

Test 1 runs application in both sender and receiver mode.

- "The following profiles are active: sender,receiver" verifies the active profiles.
- Receiver rejects "11" with exception first and then receives it again.

```console
C:\dev\work\spring_kubernetes_tutorial\code\rabbitmq-app>java -jar target\rabbitmq-app-0.0.1-SNAPSHOT.jar --spring.profiles.active=sender,receiver

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::        (v2.0.0.RELEASE)

2018-04-15 20:54:55.763  INFO CNENWUF2L1C 12028 --- [           main] c.e.rabbitmqapp.RabbitMQApplication      : Starting RabbitMQApplication v0.0.1-SNAPSHOT on CNENWUF2L1C with PID 12028 (C:\dev\work\spring_kubernetes_tutorial\code\rabbitmq-app\target\rabbitmq-app-0.0.1-SNAPSHOT.jar started by wuf2 in C:\dev\work\spring_kubernetes_tutorial\code\rabbitmq-app)
2018-04-15 20:54:55.774  INFO CNENWUF2L1C 12028 --- [           main] c.e.rabbitmqapp.RabbitMQApplication      : The following profiles are active: sender,receiver
2018-04-15 20:54:55.882  INFO CNENWUF2L1C 12028 --- [           main] s.c.a.AnnotationConfigApplicationContext : Refreshing org.springframework.context.annotation.AnnotationConfigApplicationContext@36f6e879: startup date [Sun Apr 15 20:54:55 CST 2018]; root of context hierarchy
2018-04-15 20:54:56.971  INFO CNENWUF2L1C 12028 --- [           main] trationDelegate$BeanPostProcessorChecker : Bean 'org.springframework.amqp.rabbit.annotation.RabbitBootstrapConfiguration' of type [org.springframework.amqp.rabbit.annotation.RabbitBootstrapConfiguration$$EnhancerBySpringCGLIB$$f49939f3] is not eligible for getting processed by all BeanPostProcessors (for example: not eligible for auto-proxying)
2018-04-15 20:54:58.360  INFO CNENWUF2L1C 12028 --- [           main] o.s.j.e.a.AnnotationMBeanExporter        : Registering beans for JMX exposure on startup
2018-04-15 20:54:58.376  INFO CNENWUF2L1C 12028 --- [           main] o.s.j.e.a.AnnotationMBeanExporter        : Bean with name 'rabbitConnectionFactory' has been autodetected for JMX exposure
2018-04-15 20:54:58.386  INFO CNENWUF2L1C 12028 --- [           main] o.s.j.e.a.AnnotationMBeanExporter        : Located managed bean 'rabbitConnectionFactory': registering with JMX server as MBean [org.springframework.amqp.rabbit.connection:name=rabbitConnectionFactory,type=CachingConnectionFactory]
2018-04-15 20:54:58.477  INFO CNENWUF2L1C 12028 --- [           main] o.s.c.support.DefaultLifecycleProcessor  : Starting beans in phase 2147483647
2018-04-15 20:54:58.495  INFO CNENWUF2L1C 12028 --- [cTaskExecutor-1] o.s.a.r.c.CachingConnectionFactory       : Attempting to connect to: [127.0.0.1:5672]
2018-04-15 20:54:58.608  INFO CNENWUF2L1C 12028 --- [cTaskExecutor-1] o.s.a.r.c.CachingConnectionFactory       : Created new connection: rabbitConnectionFactory#588df31b:0/SimpleConnection@50833292 [delegate=amqp://admin@127.0.0.1:5672/, localPort= 54568]
2018-04-15 20:54:58.703  INFO CNENWUF2L1C 12028 --- [           main] s.a.ScheduledAnnotationBeanPostProcessor : No TaskScheduler/ScheduledExecutorService bean found for scheduled processing
2018-04-15 20:54:58.716  INFO CNENWUF2L1C 12028 --- [           main] c.e.rabbitmqapp.RabbitMQApplication      : Started RabbitMQApplication in 3.81 seconds (JVM running for 4.592)
2018-04-15 20:54:59.232  INFO CNENWUF2L1C 12028 --- [pool-5-thread-1] com.example.rabbitmqapp.MySender         : [x] Sent 'Hello.1'
2018-04-15 20:54:59.349  INFO CNENWUF2L1C 12028 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received 'Hello.1'
2018-04-15 20:55:00.234  INFO CNENWUF2L1C 12028 --- [pool-5-thread-1] com.example.rabbitmqapp.MySender         : [x] Sent 'Hello..2'
2018-04-15 20:55:00.339  INFO CNENWUF2L1C 12028 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received 'Hello..2'
...
2018-04-15 20:55:09.250  INFO CNENWUF2L1C 12028 --- [pool-5-thread-1] com.example.rabbitmqapp.MySender         : [x] Sent 'Hello..11'
2018-04-15 20:55:09.354 ERROR CNENWUF2L1C 12028 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received Exception Hello..11
2018-04-15 20:55:09.460  INFO CNENWUF2L1C 12028 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received 'Hello..11'
```

Test 2 is to launch 2 receivers in 2 consoles and 1 sender at 1 console.

- RabbitMQ evenly distributes messages among receivers
- After receiver 2 rejects 6, 7, 8, 11, 12, receiver 1 receives them. That verifies that RabbitMQ can pass messages to another receiver if one receiver rejects a message.

```console

# receiver 1
2018-04-15 21:18:23.905  INFO CNENWUF2L1C 12840 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received 'Hello.1'
2018-04-15 21:18:25.913  INFO CNENWUF2L1C 12840 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received 'Hello...3'
2018-04-15 21:18:27.924  INFO CNENWUF2L1C 12840 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received 'Hello..5'
2018-04-15 21:18:29.036  INFO CNENWUF2L1C 12840 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received 'Hello...6'
2018-04-15 21:18:30.035  INFO CNENWUF2L1C 12840 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received 'Hello.7'
2018-04-15 21:18:31.036  INFO CNENWUF2L1C 12840 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received 'Hello..8'
2018-04-15 21:18:32.935  INFO CNENWUF2L1C 12840 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received 'Hello.10'
2018-04-15 21:18:34.042  INFO CNENWUF2L1C 12840 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received 'Hello..11'
2018-04-15 21:18:35.042  INFO CNENWUF2L1C 12840 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received 'Hello...12'

# receiver 2
2018-04-15 21:18:24.940  INFO CNENWUF2L1C 4512 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received 'Hello..2'
2018-04-15 21:18:26.920  INFO CNENWUF2L1C 4512 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received 'Hello.4'
2018-04-15 21:18:28.924 ERROR CNENWUF2L1C 4512 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received Exception Hello...6
2018-04-15 21:18:29.928 ERROR CNENWUF2L1C 4512 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received Exception Hello.7
2018-04-15 21:18:30.931 ERROR CNENWUF2L1C 4512 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received Exception Hello..8
2018-04-15 21:18:31.934  INFO CNENWUF2L1C 4512 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received 'Hello...9'
2018-04-15 21:18:33.938 ERROR CNENWUF2L1C 4512 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received Exception Hello..11
2018-04-15 21:18:34.938 ERROR CNENWUF2L1C 4512 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received Exception Hello...12
2018-04-15 21:18:35.945  INFO CNENWUF2L1C 4512 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received 'Hello.13'
```

## Reference

- RabbitMQ Spring Training [training 1](https://www.rabbitmq.com/tutorials/tutorial-one-spring-amqp.html), [training 2](https://www.rabbitmq.com/tutorials/tutorial-two-spring-amqp.html)
- [Coarse Parallel Processing Using a Work Queue](https://kubernetes.io/docs/tasks/job/coarse-parallel-processing-work-queue/)
