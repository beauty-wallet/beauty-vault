function createOnError(description, handler) {
  return function (error) {
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

function keys(protocol, callback) {
  let propertyNames = []
  let obj = protocol
  while (obj) {
    propertyNames = propertyNames.concat(Object.getOwnPropertyNames(obj))
    obj = Object.getPrototypeOf(obj)
  }

  callback(propertyNames.map((key) => [key, typeof protocol[key] === 'function' ? 'method' : 'field']))
}

const PROTOCOL_TYPE_OFFLINE = 'offline'
const PROTOCOL_TYPE_ONLINE = 'online'
const PROTOCOL_TYPE_FULL = 'full'

function loadProtocols(module, protocolType, callback, errorHandler) {
  Promise.all(
    Object.entries(module.supportedProtocols).map(([identifier, configuration]) => {
      
    })
  )
}

function load(module, identifier, protocolType, callback, errorHandler) {
  const localErrorHandler = errorHandler('load')

  module.createV3SerializerCompanion
    .then((v3SerializerCompanion => {
      loadProtocols(module, protocolType, function (protocols) {
        callback({ identifier, protocols, v3SchemaConfigurations: v3SerializerCompanion.schemas })
      }, localErrorHandler)
    }))
    .catch(localErrorHandler)
}

function callMethod(protocol, method, args, callback, errorHandler) {
  protocol[method](...args).then(callback).catch(errorHandler(method))
}

const ACTION_LOAD = 'load'
const ACTION_CALL_METHOD = 'callMethod'

function execute(module, identifier, action, handleResult, handleError) {
  const errorHandler = (description) => {
    const prefixedDescription = `[${identifier}]${description ? ' ' + description : ''}`
    return createOnError(prefixedDescription, function (error) { 
      console.error(error)
      handleError(error)
    })
  }

  try {
    switch (action.type) {
      case ACTION_LOAD:
        load(module, identifier, action.protocolType, handleResult, errorHandler)
        break
      case ACTION_CALL_METHOD:
        callMethod(module, action.method, action.args, handleResult, errorHandler)
        break
      default:
        throw new Error(`Unknown action ${action.type}`)
    }
  } catch (error) {
    errorHandler()(error)
  }
}