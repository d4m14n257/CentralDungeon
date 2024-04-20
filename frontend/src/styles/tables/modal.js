import { global } from "../global"

export const modal = {
    body: {
        ...global.colFlex,
        gap: 2,
    },
    header: {
        ...global.rowFlex,
    },
    content: {
        ...global.colFlex,
        gap: "16px"
    },
    footer: {
        ...global.rowFlex,
        justifyContent: 'flex-end'
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