export const global = {
    rowFlex: {
        display: "flex",
        flexDirection: "row"
    }, 
    colFlex: {
        display: "flex",
        flexDirection: "column"
    },
    body: {
        m: 2,
    },
    border: {
        borderRadius: 5
    },
    header: {
        display: "flex",
        flexDirection: "row",
        justifyContent: 'space-between'
    },
    iconHeader: {
        display: 'flex',
        flexDirection: 'row',
        alignItems:'center',
        gap: 2
    },
    cardProfile: {
        position: 'fixed',
        width: 400,
        right: 0,
        top: 0,
        marginTop: 8,
        borderRadius: 10,
        marginRight: '27px',
        padding: '10px 10px 0',
        gap: '4px',
        zIndex: '999'
    },
    cardHeaderSelect: {
        display: 'flex',
        flexDirection: 'column',
        gap: '5px',
        padding: '6px 6px 0',
        borderTopLeftRadius: '20px',
        borderTopRightRadius: '20px'
    },
    profileRoles: {
        display: 'flex',
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center'
    },
    profileData: {
        display: 'flex',
        flexDirection: 'row',
        alignItems: 'center',
        gap: '10px'
    },
    profileImageSelect: {
        display: "flex",
        flexDirection: "column",
        textAlign: "center",
        justifyContent: "center",
        fontSize: "21px",
        fontWeight: 500,
        width: 50,
        height: 50,
        margin: 0,
    },
    profileSettings: {
        padding: 0,
        height: '36.5px'
    },
    profileLogout: {
        display: "flex",
        flexDirection: "row",
        alignItems: "center",
        justifyContent: "center",
        borderBottomLeftRadius: "20px",
        borderBottomRightRadius: "20px",
        gap: "16px",
        height: "50px",
        border: "2px",
        padding: 0
    },
    buttonLogout: {
        width: "100%",
        borderRadius: "0 0 80px 80px",
    },
    listBody: {
        display: 'flex',
        flexDirection: 'column',
    },
    buttonFloat: {
        position: 'fixed', 
        right: {xl: 'calc((100% - 1536px) / 2)', xs: 0},
        bottom: 40,
        width: 80, 
        height: 80,
        marginRight: 3
    },
}