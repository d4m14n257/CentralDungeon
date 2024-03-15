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
        gap: 2
    },
    footer: {
        ...global.rowFlex,
        justifyContent: 'flex-end'
    }
}