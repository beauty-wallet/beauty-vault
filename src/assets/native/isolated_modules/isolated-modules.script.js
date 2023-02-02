/***** UTILS *****/
function createOnError(description, handler) {
  return (error) => {
    var fullDescription
    if (typeof error === 'string') {
      if (typeof description === 'string') {
        fullDescription = `${description} (${error})`
      } else {
        fullDescription = error
      }
    } else if (typeof error === 'object' && (typeof error.stack === 'string' || typeof error.message === 'string')) {
      if (typeof description === 'string') {
        fullDescription = `${description} (${error.stack || error.message})`
      } else {
        fullDescription = error.stack || error.message
      }
    } else {
      if (typeof description === 'string') {
        fullDescription = description
      } else {
        fullDescription = "Unknown error"
      }
    }
      
    handler(fullDescription)
  }
}

function flattened(array) {
  if (!Array.isArray(array)) return array

  return array.reduce((acc, next) => {
    return acc.concat(Array.isArray(next) ? next : [next])
  }, [])
}

function collectMethods(protocol) {
  let propertyNames = []
  let obj = protocol
  while (obj) {
    propertyNames = propertyNames.concat(Object.getOwnPropertyNames(obj))
    obj = Object.getPrototypeOf(obj)
  }

  return propertyNames.filter((key) => typeof protocol[key] === 'function')
}

/***** LOAD *****/

const PROTOCOL_TYPE_OFFLINE = 'offline'
const PROTOCOL_TYPE_ONLINE = 'online'
const PROTOCOL_TYPE_FULL = 'full'

const ISOLATED_PROTOCOL_MODE_OFFLINE = 'offline'
const ISOLATED_PROTOCOL_MODE_ONLINE = 'online'

const ISOLATED_PROTOCOL_TYPE_MAIN = 'main'
const ISOLATED_PROTOCOL_TYPE_SUB = 'sub'

function getIsolatedProtocolConfiguration(context, protocol, mode, blockExplorerMetadata, network) {
  return protocol.getMetadata()
    .then((protocolMetadata) => {
      const configuration = {
        mode,
        identifier: protocolMetadata.identifier,
        protocolMetadata,
        blockExplorerMetadata: blockExplorerMetadata ?? null,
        network: network ?? null,
        methods: collectMethods(protocol)
      }

      if (context.isSubProtocol(protocol)) {
        return Promise.all([
          protocol.getType(),
          context.hasConfigurableContract(protocol) ? protocol.getContractAddress() : Promise.resolve(undefined)
        ]).then(([subType, contractAddress]) => ({
          ...configuration,
          type: ISOLATED_PROTOCOL_TYPE_SUB,
          subType,
          contractAddress: contractAddress ?? null
        }))
      } else {
        return {
          ...configuration,
          type: ISOLATED_PROTOCOL_TYPE_MAIN
        }
      }
    })
}

function loadOfflineProtocols(context, module, protocolIdentifier) {
  return module.createOfflineProtocol(protocolIdentifier)
    .then((protocol) => {
      if (protocol === undefined) {
        return []
      }

      return getIsolatedProtocolConfiguration(context, protocol, ISOLATED_PROTOCOL_MODE_OFFLINE).then((isolatedProtocol) => [isolatedProtocol])
    })
}

function loadOnlineProtocols(context, module, protocolIdentifier, configuration) {
  return Promise.all(
    Object.entries(configuration.networks).map(([networkId, _]) => {
      return Promise.all([
        module.createOnlineProtocol(protocolIdentifier, networkId),
        module.createBlockExplorer(protocolIdentifier, networkId)
      ]).then(([protocol, blockExplorer]) => {
        if (protocol === undefined) {
          return undefined
        }

        return Promise.all([
          protocol.getNetwork(),
          blockExplorer ? blockExplorer.getMetadata() : Promise.resolve(undefined)
        ]).then(([network, blockExplorerMetadata]) => getIsolatedProtocolConfiguration(context, protocol, ISOLATED_PROTOCOL_MODE_ONLINE, blockExplorerMetadata, network))
      })
    })
  ).then((isolatedProtocols) => isolatedProtocols.filter((protocol) => protocol !== undefined))
}

function loadProtocolsFromConfiguration(context, module, protocolIdentifier, configuration, protocolType) {
  const offlineConfiguration = 
    protocolType === PROTOCOL_TYPE_OFFLINE || protocolType === PROTOCOL_TYPE_FULL || protocolType === undefined
      ? configuration.type === PROTOCOL_TYPE_OFFLINE
        ? configuration
        : configuration.type === PROTOCOL_TYPE_FULL
        ? configuration.offline
        : undefined
      : undefined

  const onlineConfiguration =
    protocolType === PROTOCOL_TYPE_ONLINE || protocolType === PROTOCOL_TYPE_FULL || protocolType === undefined
      ? configuration.type === PROTOCOL_TYPE_ONLINE
        ? configuration
        : configuration.type === PROTOCOL_TYPE_FULL
        ? configuration.online
        : undefined
      : undefined

  return Promise.all([
    offlineConfiguration ? loadOfflineProtocols(context, module, protocolIdentifier) : Promise.resolve([]),
    onlineConfiguration ? loadOnlineProtocols(context, module, protocolIdentifier, onlineConfiguration) : Promise.resolve([])
  ]).then(([offline, online]) => offline.concat(online))
}

function loadProtocols(context, module, protocolType) {
  return Promise.all(
    Object.entries(module.supportedProtocols).map(([protocolIdentifier, configuration]) => {
      loadProtocolsFromConfiguration(context, module, protocolIdentifier, configuration, protocolType)
    })
  ).then((protocols) => flattened(protocols))
}

function load(context, moduleIdentifier, action) {
  const module = context.create()

  return module.createV3SerializerCompanion()
    .then((v3SerializerCompanion) => 
      loadProtocols(context, module, action.protocolType).then((protocols) => ({ identifier: moduleIdentifier, protocols, v3SchemaConfigurations: v3SerializerCompanion.schemas }))
    )
}

/***** CALL METHOD *****/

function callOfflineProtocolMethod(context, protocolIdentifier, method, args) {
  const module = context.create()
  module.createOfflineProtocol(protocolIdentifier)
    .then((protocol) => {
      if (protocol === undefined) {
        return undefined
      }

      return protocol[method](...args)
    })
    .then((value) => ({ value }))
}

function callOnlineProtocolMethod(context, protocolIdentifier, networkId, method, args) {
  const module = context.create()
  module.createOnlineProtocol(protocolIdentifier, networkId)
    .then((protocol) => {
      if (protocol === undefined) {
        return undefined
      }

      return protocol[method](...args)
    })
    .then((value) => ({ value }))
}

function callBlockExplorerMethod(context, protocolIdentifier, networkId, method, args) {
  const module = context.create()
  module.createBlockExplorer(protocolIdentifier, networkId)
    .then((blockExplorer) => {
      if (blockExplorer === undefined) {
        return undefined
      }

      return blockExplorer[method](...args)
    })
    .then((value) => ({ value }))
}

function callV3SerializerCompanionMethod(context, method, args) {
  const module = context.create()
  module.createV3SerializerCompanion()
    .then((v3SerializerCompanion) => {
      if (v3SerializerCompanion === undefined) {
        return undefined
      }

      return v3SerializerCompanion[method](...args)
    })
    .then((value) => ({ value }))
}

function callMethod(context, action) {
  switch (action.target) {
    case 'offlineProtocol':
      return callOfflineProtocolMethod(context, action.protocolIdentifier, action.method, action.args)
    case 'onlineProtocol':
      return callOnlineProtocolMethod(context, action.protocolIdentifier, action.networkId, action.method, action.args)
    case 'blockExplorer':
      return callBlockExplorerMethod(context, action.protocolIdentifier, action.networkId, action.method, action.args)
    case 'v3SerializerCompanion':
      return callV3SerializerCompanionMethod(context, action.method, action.args)
  }
}

/***** EXECUTE *****/

const ACTION_LOAD = 'load'
const ACTION_CALL_METHOD = 'callMethod'

function execute(context, moduleIdentifier, action, handleResult, handleError) {
  const errorHandler = (description) => {
    const prefixedDescription = `[${moduleIdentifier}]${description ? ' ' + description : ''}`
    return createOnError(prefixedDescription, (error) => { 
      console.error(error)
      handleError(error)
    })
  }

  try {
    switch (action.type) {
      case ACTION_LOAD:
        load(context, moduleIdentifier, action).then(handleResult).catch(errorHandler('load'))
        break
      case ACTION_CALL_METHOD:
        callMethod(context, action).then(handleResult).catch(errorHandler('call method'))
        break
      default:
        throw new Error(`Unknown action ${action.type}`)
    }
  } catch (error) {
    errorHandler()(error)
  }
}