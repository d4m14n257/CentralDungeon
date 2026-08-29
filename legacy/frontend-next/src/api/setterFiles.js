export const setterFiles = async ({
    id = null, 
    others = null,
    data, 
    url
    }) => {
    try {
        let complete_url = `${process.env.NEXT_PUBLIC_SERVER}/${url}`;

        if(id)
            complete_url += `/${id}`;

        if(others)
            complete_url += `/${others}`

        const option = {
            method: 'POST',
            body: data
        }
        const response = await fetch(complete_url, option)
        .catch((err) => {
            throw {...err.cause, status: 500}
        })

        const status = response.status
        const files = await response.json()

        return { status, files }

    } catch (err) {
        return err
    }
}  