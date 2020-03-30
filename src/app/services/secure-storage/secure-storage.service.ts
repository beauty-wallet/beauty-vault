import { Injectable, Inject } from '@angular/core'
import { SecurityUtilsPlugin } from 'src/app/capacitor-plugins/definitions'
import { SECURITY_UTILS_PLUGIN } from 'src/app/capacitor-plugins/injection-tokens'

export interface SecureStorage {
  init(): Promise<void>
  setItem(key: string, value: string): Promise<void>
  getItem(key: string): Promise<any>
  setupRecoveryPassword(key: string, value: string): Promise<any>
  removeItem(key: string): Promise<void>
}

@Injectable({
  providedIn: 'root'
})
export class SecureStorageService {

  constructor(@Inject(SECURITY_UTILS_PLUGIN) private readonly securityUtils: SecurityUtilsPlugin) {}

  public isDeviceSecure(): Promise<any> {
    return this.securityUtils.isDeviceSecure({
      alias: 'airgap-secure-storage',
      isParanoia: false
    })
  }

  public secureDevice(): Promise<void> {
    return this.securityUtils.secureDevice({
      alias: 'airgap-secure-storage',
      isParanoia: false
    })
  }

  public async get(alias: string, isParanoia: boolean): Promise<SecureStorage> {
    const securityUtils = this.securityUtils

    await securityUtils.initStorage({
      alias,
      isParanoia
    })

    return {
      init() {
        return securityUtils.initStorage({
          alias,
          isParanoia
        })
      },
      setItem(key, value) {
        return securityUtils.setItem({
          alias,
          isParanoia,
          key,
          value
        })
      },
      setupRecoveryPassword(key, value) {
          return securityUtils.setupRecoveryPassword({
            alias,
            isParanoia,
            key, 
            value
        })
      },
      getItem(key) {
        return securityUtils.getItem({
          alias,
          isParanoia,
          key
        }).catch((error: string) => {
          if (error.toLowerCase().includes('item corrupted')) {
            throw new Error('Could not read from the secure storage.')
          }

          throw error
        })
      },
      removeItem(key) {
        return securityUtils.removeItem({
          alias,
          isParanoia,
          key
        })
      }
    }
  }
}
