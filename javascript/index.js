const {getSize} = require('import-cost/dist/src/packageInfo');

class ImportCostPlugin {
    onMessage(p, messageWriter) {
        const {seq, arguments: packageInfo} = JSON.parse(p);
        const config = {maxCallTime: Infinity, concurrent: true};
        getSize(packageInfo, config).then((pkg) => {
            messageWriter.write(JSON.stringify({request_seq: seq, package: pkg}))
        }).catch((e) => {
            messageWriter.write(JSON.stringify({request_seq: seq, error: e}))
        })
    }
}

class ImportCostPluginFactory {
    create() {
        return {languagePlugin: new ImportCostPlugin()}
    }
}

module.exports = {
    factory: new ImportCostPluginFactory()
};
