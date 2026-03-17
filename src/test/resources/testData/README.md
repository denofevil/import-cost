# Test Data for Import Cost Plugin

This directory contains test fixtures for the Import Cost integration tests.

## Structure

```
testData/
├── node_modules/          # Fake npm packages for testing
│   ├── react/
│   │   ├── package.json
│   │   └── index.js
│   ├── lodash/
│   │   ├── package.json
│   │   └── lodash.js
│   ├── moment/
│   │   ├── package.json
│   │   └── moment.js
│   └── rxjs/
│       ├── package.json
│       └── index.js
├── package.json           # Test project package.json
└── test.js               # Sample test file
```

## Fake Packages

These are **simulated npm packages** that provide realistic APIs without the bulk of real packages.

### Why Fake Packages?

1. **Speed**: Tests run in seconds instead of minutes
2. **No Network**: Tests work offline, no npm registry access needed
3. **Deterministic**: Sizes are predictable and consistent across runs
4. **Size**: Each package is 2-5KB instead of 100KB+ for real packages
5. **Version Control**: Packages are committed to git, no installation needed

### Package Details

#### react (v18.0.0)
- **Size**: ~2KB
- **API**: createElement, Component, hooks (useState, useEffect, etc.)
- **Purpose**: Tests JSX/React imports

#### lodash (v4.17.21)
- **Size**: ~5KB
- **API**: Array, Collection, Object, String, Function utilities
- **Purpose**: Tests named imports (`import { debounce } from 'lodash'`)

#### moment (v2.29.4)
- **Size**: ~3KB
- **API**: Date parsing, formatting, manipulation
- **Purpose**: Tests require() calls

#### rxjs (v7.8.0)
- **Size**: ~5KB
- **API**: Observable, Subject, operators (map, filter, etc.)
- **Purpose**: Tests namespace imports (`import * as rxjs from 'rxjs'`)

## How Tests Use This Data

1. `ImportCostIntegrationTest.setUp()` copies `node_modules/` to the test project
2. Test creates JavaScript files with imports from these packages
3. Import Cost service analyzes the imports
4. JavaScript service bundles the fake packages using webpack
5. Sizes are calculated and returned to the test

## Maintaining Fake Packages

When updating fake packages:

1. Keep the API surface realistic but minimal
2. Ensure `package.json` has correct `main` entry point
3. Export both CommonJS and ES6 module formats
4. Add enough code to produce measurable sizes (>1KB)
5. Don't add unnecessary dependencies

## Real vs Fake Size Comparison

| Package | Real Size | Fake Size | Purpose |
|---------|-----------|-----------|---------|
| react   | ~140KB    | ~2KB      | Component framework |
| lodash  | ~530KB    | ~5KB      | Utility functions |
| moment  | ~230KB    | ~3KB      | Date handling |
| rxjs    | ~180KB    | ~5KB      | Reactive programming |

The fake packages are 30-100x smaller but still provide enough code for meaningful webpack bundle size calculations.
