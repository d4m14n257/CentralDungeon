export const getter = async ({
    id = null,
    others = null, 
    url
}) => {
    try {
        let complete_url = `${process.env.NEXT_PUBLIC_SERVER}/${url}`;
        
        if(id)
            complete_url += `/${id}`;

        if(others)
            complete_url += `/${others}`;

        const response = await fetch(complete_url).catch((err) => {
            throw {...err.cause, status: 500}
        });

        const status = response.status;
        const data = await response.json();

        return {...data, status: status};

    } catch (err) {  
        return err;
    }
}

//TODO: Check the response data.