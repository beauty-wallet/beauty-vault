const fs = require('fs')
const path = require('path')

const browserify = require('browserify')

const rootdir = './'
const assetsdir = path.join(rootdir, 'src/assets')
const modules = [
  path.join(rootdir, 'node_modules/@airgap/aeternity'),
  path.join(rootdir, 'node_modules/@airgap/astar'),
  path.join(rootdir, 'node_modules/@airgap/bitcoin'),
  path.join(rootdir, 'node_modules/@airgap/ethereum'),
  path.join(rootdir, 'node_modules/@airgap/groestlcoin'),
  path.join(rootdir, 'node_modules/@airgap/icp'),
  path.join(rootdir, 'node_modules/@airgap/moonbeam'),
  path.join(rootdir, 'node_modules/@airgap/polkadot'),
  path.join(rootdir, 'node_modules/@airgap/tezos')
]

function createAssetModule(modulePath) {
  const packageJson = require(`./${path.join(modulePath, 'package.json')}`)
  const namespace = modulePath.split('/').slice(-1)[0]
  const outputDir = path.join(assetsdir, `libs/${namespace}`)
  const outputFile = 'index.browserify.js'

  fs.mkdirSync(outputDir, { recursive: true })

  browserify()
    .add(`${modulePath}/v1/module.js`, { standalone: namespace })
    .bundle()
    .pipe(fs.createWriteStream(path.join(outputDir, outputFile)))


  const manifest = {
    name: packageJson.name,
    version: packageJson.version,
    author: packageJson.author,
    signature: "" /* TODO */,
    src: {
      namespace
    },
    include: [
      outputFile
    ]
  }

  fs.writeFileSync(path.join(outputDir, 'manifest.json'), JSON.stringify(manifest, null, 2), 'utf8')
}

modules.forEach((path) => createAssetModule(path))