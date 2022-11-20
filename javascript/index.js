const {getSize} = require('import-cost/src/package-info');

class ImportCostPlugin {
    // noinspection JSUnusedGlobalSymbols (service API)
    onMessage(p, messageWriter) {
        const {seq, arguments: packageInfo} = JSON.parse(p);
        const config = {concurrent: true};
        getSize(packageInfo, config).then((pkg) => {
            messageWriter.write(JSON.stringify({request_seq: seq, package: pkg}))
        }).catch((e) => {
            messageWriter.write(JSON.stringify({request_seq: seq, error: e}))
        })
    }
}

process.on('uncaughtException', (_) => {

});

class ImportCostPluginFactory {
    // noinspection JSUnusedGlobalSymbols (service API)
    create() {
        return {languagePlugin: new ImportCostPlugin()}
    }
}

module.exports = {
    factory: new ImportCostPluginFactory()
};

//debugging code
// function run() {
//     let command = "{\"sessionId\":1668949352429,\"seq\":0,\"type\":\"request\",\"command\":\"import-cost\",\"arguments\":{\"fileName\":\"/Users/anstarovoyt/IdeaProjects/untitled8/src/App.vue\",\"name\":\"./components/HelloWorld.vue\",\"line\":1,\"string\":\"import HelloWorld from \u0027./components/HelloWorld.vue\u0027; console.log(HelloWorld);\"}}"
//
//     new ImportCostPlugin().onMessage(command, {
//         write(text) {
//             console.log(text)
//         }
//     });
// }
//
// run();