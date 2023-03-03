let LogLevel = < Info | Error | Warn | Debug >
let format = "$time - $loggername - $prio - $msg" : Text
let fileHandlers = \(path : Text) -> \(level : LogLevel) -> {_1 = path, _2 = level, _3 = format}
let levelOverride = \(component : Text) -> \(level : LogLevel) -> {_1 = component, _2 = level}
in
{ explorerConfig =
    { explorerUri = "https://explorer.spectrum.fi"
    },
  ordersProducerConfig =
    { producerBrokers = ["kafkaV21:9092", "kafkaV22:9092", "kafkaV22:9092"]
    , producerTimeout = 1000
    },
  poolsProducerConfig =
    { producerBrokers = ["kafkaV21:9092", "kafkaV22:9092", "kafkaV22:9092"]
    , producerTimeout = 1000
    },
  ordersTopicName = "orders-topic",
  poolsTopicName = "pools-topic-name",
  trackerProgrammConfig =
    { pollTime = 2
    },
  redisSettings =
    { redisHost = "redisV2"
    , redisPort = "6379"
    },
  trackerSettings =
    { limit = 100
    },
  retry =
    { sleepTime = 1000000
    },
  loggingConfig =
    { fileHandlers = [fileHandlers "./logs/tracker.log" LogLevel.Info]
    , levelOverrides = [] : List { _1 : Text, _2 : LogLevel }
    , rootLogLevel = LogLevel.Info
    }
}