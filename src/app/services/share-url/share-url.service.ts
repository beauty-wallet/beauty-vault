import { SerializerService } from '@airgap/angular-core'
import { AccountShareResponse, AirGapWallet, generateId, IACMessageDefinitionObject, IACMessageType } from '@airgap/coinlib-core'
import { Injectable } from '@angular/core'
import { Secret } from 'src/app/models/secret'

import { serializedDataToUrlString } from '../../utils/utils'
import { SecretsService } from '../secrets/secrets.service'

@Injectable({
  providedIn: 'root'
})
export class ShareUrlService {
  constructor(private readonly serializerService: SerializerService, private readonly secretsService: SecretsService) {
    //
  }

  public async generateShareURL(wallet: AirGapWallet): Promise<string> {
    const secret: Secret | undefined = this.secretsService.findByPublicKey(wallet.publicKey)

    const accountShareResponse: AccountShareResponse = {
      publicKey: wallet.publicKey,
      isExtendedPublicKey: wallet.isExtendedPublicKey,
      derivationPath: wallet.derivationPath,
      masterFingerprint: wallet.masterFingerprint,
      groupId: secret.fingerprint,
      groupLabel: secret.label
    }

    const deserializedTxSigningRequest: IACMessageDefinitionObject = {
      id: generateId(10),
      protocol: wallet.protocol.identifier,
      type: IACMessageType.AccountShareResponse,
      payload: accountShareResponse
    }

    const serializedTx: string[] = await this.serializerService.serialize([deserializedTxSigningRequest])

    return serializedDataToUrlString(serializedTx, 'airgap-wallet://')
  }
}
