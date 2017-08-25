# Import Cost for WebStorm
This [plugin](https://plugins.jetbrains.com/plugin/9970-import-cost) displays the size of the imported JavaScript package right in the editor. 
It can be installed in WebStorm, IntelliJ IDEA Ultimate, PhpStorm, PyCharm Pro, and RubyMine v2017.2 and above. 

This plugin uses the [Import Cost](https://github.com/wix/import-cost) module that uses webpack with babili-webpack-plugin to calculate the size of the imported module.

<img src="https://blog.jetbrains.com/webstorm/files/2017/08/import-cost.gif" width="500">

You can read about the original idea behind the Import Cost module in this [blog post by Yair Haimovitch](https://medium.com/@yairhaimo/keep-your-bundle-size-under-control-with-import-cost-vscode-extension-5d476b3c5a76).

## Installing the plugin
In the IDE open `Preferences | Plugins` and click `Browse repositories...`. Start typing `Import Cost` in the search bar to find the plugin, then click `Install`.

Or you can download it from the [JetBrains Plugins Repository](https://plugins.jetbrains.com/plugin/9970-import-cost).

## Contributing
Please report any issue with the plugin on [GitHub](https://github.com/denofevil/import-cost/issues). We welcome your pull requests.

The plugin contains a simple JavaScript service that communicates with the [Import Cost](https://github.com/wix/import-cost) JavaScript module. All the IDE API calls are written in [Kotlin](https://kotlinlang.org/). Check out the [IntelliJ Platform SDK Documentation](http://www.jetbrains.org/intellij/sdk/docs/welcome.html) to learn more about plugin development from IntelliJ Platform.
