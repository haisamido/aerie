# schemas

This folder contains [JSON Schema](https://json-schema.org/) `.json` files for all the shared types in the Aerie project.

| Conversion | Library |
| ---------- | ------- |
| JSON Schema -> [Java](https://www.oracle.com/java/) | [jsonschema2pojo](http://www.jsonschema2pojo.org/) |
| JSON Schema -> [TypeScript](https://www.typescriptlang.org/) | [quicktype](https://quicktype.io/) |

# Setup

## OSX

First install [Homebrew](https://brew.sh/) and [Node](https://nodejs.org/en/) then do:

```
brew install jsonschema2pojo
npm i
```

# Build

```
npm run build
```