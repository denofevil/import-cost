const {getSize} = require('import-cost/src/package-info');

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


//debugging code
// function run() {
//     let test = "/Users/anstarovoyt/IdeaProjects/untitled7/src/index.js";
//     let packageInfo = {
//         fileName: test,
//         line: 0,
//         name: "react",
//         string: "import React from 'react'"
//     }
//
//     new ImportCostPlugin().onMessage(JSON.stringify({seq:1, arguments: packageInfo}), {
//         write(text) {
//             console.log(text)
//         }
//     });
// }
//
// run();