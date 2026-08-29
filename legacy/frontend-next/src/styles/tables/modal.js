import { global } from "../global"

export const modal = {
    body: {
        ...global.colFlex,
        gap: 2,
    },
    header: {
        ...global.colFlex,
        gap: 1
    },
    content: {
        ...global.colFlex,
        gap: "12px"
    },
    footer: {
        ...global.rowFlex,
        justifyContent: 'flex-end',
        gap: 1
    },
    formArea: {
        minWidth: 500,
        maxWidth: 786,
        width: '100%'
    },
    icon: {
        width: 60,
        height: 60
    }
}