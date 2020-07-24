import { AlertButton } from '@ionic/core'
import { Injectable } from '@angular/core'
import { AlertController } from '@ionic/angular'
import { handleErrorLocal, ErrorCategory } from '../error-handler/error-handler.service'
import { TranslateService } from '@ngx-translate/core'

@Injectable({
  providedIn: 'root'
})
export class AlertService {
  constructor(private readonly alertCtrl: AlertController, private readonly translateService: TranslateService) {}

  public async showTranslatedAlert(
    title: string,
    message: string,
    backdropDismiss: boolean,
    buttons: AlertButton[],
    _subHeader?: string
  ): Promise<void> {
    return new Promise((reject) => {
      const translationKeys = _subHeader
        ? [title, message, ...buttons.map((button) => button.text), _subHeader]
        : [title, message, ...buttons.map((button) => button.text)]

      this.translateService.get(translationKeys).subscribe(async (values) => {
        const translatedButtons = buttons.map((button) => {
          button.text = values[button.text]
          return button
        })

        const alert = await this.alertCtrl.create({
          header: values[title],
          subHeader: values[_subHeader],
          message: values[message],
          backdropDismiss: backdropDismiss,
          buttons: translatedButtons
        })

        alert.present().catch(() => {
          reject()
          handleErrorLocal(ErrorCategory.IONIC_ALERT)
        })
      })
    })
  }
}

// scheme-routing.service.ts
// deep-link.service.ts
// secret-show.page.ts
// secret.service.ts
// transaction-detail.page.ts
// permissions.service.ts
// secret-edit.page.ts

// secret-edit.page.ts
