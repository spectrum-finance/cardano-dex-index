let LogLevel = < Info | Error | Warn | Debug >
let format = "$time - $loggername - $prio - $msg" : Text
let fileHandlers = \(path : Text) -> \(level : LogLevel) -> {_1 = path, _2 = level, _3 = format}
let levelOverride = \(component : Text) -> \(level : LogLevel) -> {_1 = component, _2 = level}
in
{ explorerConfig =
    { explorerUri = "https://explorer.spectrum.fi"
    },
  txEventsProducerConfig =
    { producerBrokers = ["kafkaV21:9092"]
    , producerTimeout = 1000
    },
  ordersProducerConfig =
    { producerBrokers = ["kafkaV21:9092"]
    , producerTimeout = 1000
    },
  poolsProducerConfig =
    { producerBrokers = ["kafkaV21:9092"]
    , producerTimeout = 1000
    },
  txEventsTopicName = "tx-events",
  ordersTopicName = "orders-topic",
  poolsTopicName = "pools-topic-name",
  trackerProgrammConfig =
    { pollTime = 2
    },
  scriptsConfig =
    { swapScriptPath    = "/scripts/swap.uplc"
    , depositScriptPath = "/scripts/deposit.uplc"
    , redeemScriptPath  = "/scripts/redeem.uplc"
    , poolScriptPath    = "/scripts/pool.uplc"
    },
  redisSettings =
    { redisHost = "0.0.0.0"
    , redisPort = "6379"
    },
  trackerSettings =
    { limit = 100
    },
  retry =
    { sleepTime = 1000000
    },
  eventSourceConfig =
    { startAt =
        { slot = 2538148
        , hash = "438f1ea173969e5f130324843f923f14abc187078452aa7e83de467e17a89cb8"
        }
    },
  lederHistoryConfig =
    { storePath       = "./data/amm-executor"
    , createIfMissing = True
    },
  networkConfig =
    { cardanoNetworkId = 2
    },
  nodeConfigPath = "/var/cardano/config/config.json",
  ledgerSyncConfig =
    { nodeSocketPath = "/ipc/node.socket"
    , maxInFlight    = 256
    },
  loggingConfig =
    { fileHandlers = [fileHandlers "./logs/tracker.log" LogLevel.Info]
    , levelOverrides = [] : List { _1 : Text, _2 : LogLevel }
    , rootLogLevel = LogLevel.Info
    }
}