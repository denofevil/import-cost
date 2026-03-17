# Import Cost Plugin Specification

## Overview

Import Cost is a JetBrains IDE plugin that displays the bundle size of imported JavaScript packages directly in the editor. It helps developers monitor and control their bundle sizes by showing real-time size information for npm package imports.

## Supported IDEs

- All JetBrains IDEs (with JavaScript support)

## Core Functionality

### What It Does

The plugin calculates and displays the size (normal and gzipped) of imported npm packages inline in the editor. It provides visual feedback using color-coding based on configurable size thresholds.

### Supported Import Styles

1. **Default importing**: `import Func from 'utils';`
2. **Entire content importing**: `import * as Utils from 'utils';`
3. **Selective importing**: `import {Func} from 'utils';`
4. **Selective importing with alias**: `import {orig as alias} from 'utils';`
5. **Submodule importing**: `import Func from 'utils/Func';`
6. **Require statements**: `const Func = require('utils').Func;`
7. **Dynamic imports**: `import('utils')`

### Scope

- **Analyzes**: Only non-relative imports (npm packages)
- **Ignores**: Relative imports to local files (e.g., `./components/Header`)

## Architecture

### Two-Layer System

#### 1. JavaScript Layer (`javascript/`)

**Purpose**: Calculates actual bundle sizes using webpack

**Implementation**:
- Entry point: `javascript/index.js`
- Dependencies:
  - `import-cost` (v3.2.0) - core size calculation library
  - `terser-webpack-plugin` - for minification
  - `native-fs-adapter` - file system access
  - `vscode-uri` - URI handling
  - `fs-extra` - enhanced file operations

**How it works**:
- Receives import statements from the Kotlin layer via JSON-RPC
- Uses webpack with babili-webpack-plugin to calculate bundle size
- Returns both normal and gzipped sizes
- Runs with `concurrent: false` config to reduce system load

**Protocol**:
```javascript
// Request format
{
  "seq": <request_id>,
  "arguments": {
    "fileName": "/path/to/file.js",
    "name": "package-name",
    "line": <line_number>,
    "string": "import statement code"
  }
}

// Response format
{
  "request_seq": <request_id>,
  "package": {
    "size": <bytes>,
    "gzip": <bytes>
  }
}
```

#### 2. Kotlin Layer (`src/main/kotlin/`)

**Purpose**: IDE integration, UI rendering, PSI parsing, and orchestration

**Key Components**:

##### ImportCostLanguageService.kt
- **Main orchestration service** (Project-level service)
- Manages communication with JavaScript service via JSLanguageServiceQueue
- Parses JavaScript/TypeScript files to find import statements
- Implements caching strategy (ConcurrentHashMap by file path + line number)
- Uses Kotlin coroutines with Flow for async processing
- Debouncing: 1 second delay before processing document changes
- Document listener tracks changes and invalidates cache when needed

**Key Methods**:
- `getImportSize(file, line)`: Returns cached or calculated size for import at given line
- `buildRequests(document, file)`: Uses PSI visitor to find all imports
- `compileImportString()`: Converts import declarations to executable code for webpack

##### ImportCostCodeVisionProvider.kt
- **Code Vision implementation** (inline hints above code)
- Extends `CodeVisionProviderBase`
- Displays size information as clickable rich text
- Color-coded based on size thresholds (INFO/WARNING/ERROR)
- Clicking opens settings dialog

##### ImportCostLinePainter.kt
- **Alternative display method** (end-of-line annotations)
- Implements `EditorLinePainter`
- Used when Code Vision is disabled
- Shows size info at end of import lines

##### ImportCostSettings.kt
- **Persistent settings** (Project-level component)
- Stored in `import-cost.xml`
- Configuration options:
  - `isCodeVision`: Toggle between Code Vision and line painter
  - `textTemplate`: Display format (default: `$size (gzip $gsize)`)
  - `errorLimit`: Size threshold for error highlighting (default: 100 KB)
  - `warningLimit`: Size threshold for warning highlighting (default: 50 KB)

##### ImportCostSizeKind.kt
- **Size classification** and color mapping
- Three levels: INFO, WARNING, ERROR
- Maps sizes to editor color schemes:
  - ERROR: Uses `ERRORS_ATTRIBUTES` color
  - WARNING: Uses `WARNINGS_ATTRIBUTES` color
  - INFO: Uses `LINE_COMMENT` color

##### ServiceProtocol.kt
- **Language service protocol** implementation
- Extends `JSLanguageServiceNodeStdProtocolBase`
- Configures Node.js service connection
- Points to `lib/index.js` as plugin entry point

## Workflow

### 1. Initialization
```
IDE starts → ImportCostLanguageService created → JavaScript service spawned
```

### 2. File Opening
```
File opened → Document listener attached → Import statements parsed → Requests queued
```

### 3. Size Calculation Flow
```
1. User edits document
2. DocumentListener.documentChanged() triggered
3. Cache invalidated for changed lines
4. EvalDocumentRequest emitted to Flow
5. Flow debounces for 1 second
6. buildRequests() parses PSI for imports
7. Requests sent to JavaScript service
8. Responses cached in ConcurrentHashMap
9. UI update request emitted
10. After 1 second debounce, FileContentUtilCore.reparseFiles() called
11. Code Vision provider reads cached sizes
12. Inline hints rendered in editor
```

### 4. Caching Strategy
- **Key**: `${file.path}:${lineNumber}`
- **Value**: `Sizes(size: Long, gzip: Long)`
- **Invalidation**:
  - Line content changes → that line's cache cleared
  - Newline added/removed → entire file cache cleared
  - File modified → cache updated asynchronously

### 5. PSI Visitor Logic
The plugin uses `JSRecursiveWalkingElementVisitor` to find:
- `ES6ImportDeclaration` → extracts module path and converts to executable import
- `ES6ImportCall` → extracts dynamic import path
- `JSCallExpression.isRequireCall` → extracts require() calls

**Filter**: Only non-relative paths (checked via `JSFileReferencesUtil.isRelative()`)

## Display Modes

### Code Vision Mode (Default)
- Shows size above import statement
- Rendered as inline hint using Code Insight framework
- Color-coded rich text
- Clickable to open settings

### Line Painter Mode
- Shows size at end of import line
- Uses `EditorLinePainter` API
- Italic text with color coding
- Less intrusive than Code Vision

## Configuration

### Settings Dialog
Accessible via: `Preferences | Tools | Import Cost`

Options:
1. **Display Mode**: Code Vision vs Line Painter
2. **Text Template**: Customize format using `$size` and `$gsize` placeholders
3. **Warning Threshold**: Size in KB for warning color (default: 50)
4. **Error Threshold**: Size in KB for error color (default: 100)

### Size Thresholds
- **< Warning**: Green/gray (INFO)
- **≥ Warning && < Error**: Yellow/orange (WARNING)
- **≥ Error**: Red (ERROR)

## Build System

### Gradle Configuration (`build.gradle.kts`)

**Plugins**:
- `org.jetbrains.kotlin.jvm` (v2.3.20)
- `org.jetbrains.intellij.platform` (v2.13.1)

**Target Platform**:
- WebStorm 261.22158.36
- Since build: 261
- Until build: 261.*

**JavaScript Packaging**:
- `PrepareSandboxTask` hook copies `javascript/` folder to `lib/` in plugin distribution
- JavaScript dependencies bundled with plugin

**Version**: 1.4.261

### JavaScript Dependencies

From `javascript/package.json`:
- `import-cost@3.2.0` - Core size calculation
- `terser-webpack-plugin@^5.3.1` - Minification
- `native-fs-adapter@^1.0.0` - File system (with postinstall patch)
- `vscode-uri@^3.0.3` - URI utilities
- `fs-extra@^10.1.0` - File operations

**Known Issue**: `native-fs-adapter` requires postinstall sed patch (see https://github.com/wix/import-cost/issues/279)

## Plugin Metadata

**Plugin ID**: `ImportCost`
**Name**: Import Cost
**Vendor**: Dennis Ushakov, Andrey Starovoyt
**Repository**: https://github.com/denofevil/import-cost
**Marketplace**: https://plugins.jetbrains.com/plugin/9970-import-cost

**Dependencies**:
- `com.intellij.modules.lang`
- `JavaScript` (bundled plugin)
- `com.intellij.modules.xml`

## Extension Points

The plugin registers these IntelliJ Platform extensions:

1. `editor.linePainter` → `ImportCostLinePainter`
2. `codeInsight.daemonBoundCodeVisionProvider` → `ImportCostCodeVisionProvider`
3. `config.codeVisionGroupSettingProvider` → `ImportCostCodeVisionGroupSettingProvider`
4. `projectService` → `ImportCostSettings`
5. `projectConfigurable` → `ImportCostConfigurable`

## Performance Optimizations

1. **Debouncing**: 1-second delay on document changes prevents excessive calculations
2. **Caching**: Results cached per file+line, only recalculated when line changes
3. **Concurrent Limiting**: JavaScript service runs with `concurrent: false`
4. **Lazy Evaluation**: Size calculation only triggered for visible files with listeners
5. **Smart Invalidation**: Only affected lines cleared from cache, not entire file (unless newlines change)
6. **Read Action Optimization**: `needReadActionToCreateState()` returns false

## Error Handling

- JavaScript service errors caught and logged
- Failed size calculations return `failedSize = Sizes(0, 0)`
- Invalid files or non-local files silently ignored
- Uncaught exceptions in JavaScript process suppressed to prevent crashes

## File Type Support

**Supported**:
- JavaScript files (`*.js`, `*.jsx`)
- TypeScript files (`*.ts`, `*.tsx`)
- HTML files with embedded scripts
- Vue files (`*.vue`)
- Any `HtmlCompatibleFile` or `JSFile`

**Condition**: Must be in local file system and valid

## Credits

Based on the original [Import Cost](https://github.com/wix/import-cost) module by Wix.
Original idea: [Keep Your Bundle Size Under Control](https://medium.com/@yairhaimo/keep-your-bundle-size-under-control-with-import-cost-vscode-extension-5d476b3c5a76) by Yair Haimovitch
