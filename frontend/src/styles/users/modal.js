import { global } from "../global"

export const styles = {
    body: {
        ...global.colFlex,
    },
    header: {
        ...global.rowFlex,
        justifyContent: 'space-between',
        alignItems: 'baseline'
    },
    img: {
        height: 32
    },
    flag: {
        ...global.rowFlex,
        gap: 2
    },
    avatar: {
        height: 48,
        width: 48
    },
    info: {
        ...global.colFlex,
        gap: 1,

    },
    avatarContent: {
        ...global.rowFlex,
        alignItems: 'center',
        gap: 1
    },
    flagContent: {
        ...global.colFlex,
        alignItems: 'end',
    },
    content: {
        ...global.colFlex,
        marginTop: 3,
        gap: 1
    }
}