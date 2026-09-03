import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'

import { DEFAULT_LANGUAGE, resolveInitialLanguage } from '@/config/language'

import enAdmin from '@/locales/en/admin.json'
import enAuth from '@/locales/en/auth.json'
import enCatalogs from '@/locales/en/catalogs.json'
import enCommon from '@/locales/en/common.json'
import enDev from '@/locales/en/dev.json'
import enHelp from '@/locales/en/help.json'
import enMaster from '@/locales/en/master.json'
import enNotifications from '@/locales/en/notifications.json'
import enOnboarding from '@/locales/en/onboarding.json'
import enRegistrations from '@/locales/en/registrations.json'
import enTables from '@/locales/en/tables.json'
import enUsers from '@/locales/en/users.json'

import admin from '@/locales/es/admin.json'
import auth from '@/locales/es/auth.json'
import catalogs from '@/locales/es/catalogs.json'
import common from '@/locales/es/common.json'
import dev from '@/locales/es/dev.json'
import help from '@/locales/es/help.json'
import master from '@/locales/es/master.json'
import notifications from '@/locales/es/notifications.json'
import onboarding from '@/locales/es/onboarding.json'
import registrations from '@/locales/es/registrations.json'
import tables from '@/locales/es/tables.json'
import users from '@/locales/es/users.json'

/**
 * The two languages the application speaks (#198).
 *
 * <p>Both bundles ship with the application rather than being fetched on demand: together they are a
 * few tens of kilobytes of JSON, and a screen that has to wait on the network before it can render
 * its own labels is worse than a slightly larger bundle. It is the same reasoning as the country
 * list in `lib/countries.ts`.
 *
 * <p>`fallbackLng` is Spanish and not the current language: a key that has not been translated yet
 * shows its Spanish text, which is readable, instead of showing the key itself, which is not.
 */
void i18n.use(initReactI18next).init({
  lng: resolveInitialLanguage(),
  fallbackLng: DEFAULT_LANGUAGE,
  defaultNS: 'common',
  resources: {
    es: { common, auth, onboarding, catalogs, tables, registrations, notifications, master, admin, users, help, dev },
    en: {
      common: enCommon,
      auth: enAuth,
      onboarding: enOnboarding,
      catalogs: enCatalogs,
      tables: enTables,
      registrations: enRegistrations,
      notifications: enNotifications,
      master: enMaster,
      admin: enAdmin,
      users: enUsers,
      help: enHelp,
      dev: enDev,
    },
  },
  interpolation: { escapeValue: false },
})

/**
 * Keeps `<html lang>` in step with the language in use (#198).
 *
 * It lives here and not in a component because it has to hold everywhere, including the screens
 * that render before anybody is signed in: a page that claims Spanish while showing English is read
 * out by a screen reader with the wrong phonemes, which is worse than either language alone.
 */
function syncDocumentLanguage(language: string) {
  if (typeof document !== 'undefined') {
    document.documentElement.lang = language
  }
}

syncDocumentLanguage(i18n.language)
i18n.on('languageChanged', syncDocumentLanguage)

export default i18n
