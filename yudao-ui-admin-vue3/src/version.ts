// 从 package.json 读取版本号，兼容 dev 和 production 模式
import pkg from '../package.json'
export const APP_VERSION: string = pkg.version
