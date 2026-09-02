import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'

import admin from '@/locales/es/admin.json'
import auth from '@/locales/es/auth.json'
import common from '@/locales/es/common.json'
import dev from '@/locales/es/dev.json'
import master from '@/locales/es/master.json'
import notifications from '@/locales/es/notifications.json'
import onboarding from '@/locales/es/onboarding.json'
import registrations from '@/locales/es/registrations.json'
import tables from '@/locales/es/tables.json'
import users from '@/locales/es/users.json'

void i18n.use(initReactI18next).init({
  lng: 'es',
  fallbackLng: 'es',
  defaultNS: 'common',
  resources: {
    es: { common, auth, onboarding, tables, registrations, notifications, master, admin, users, dev },
  },
  interpolation: { escapeValue: false },
})

export default i18n
