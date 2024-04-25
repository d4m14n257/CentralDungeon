export const getter = async ({
    user_id = null,
    other_id = null, 
    url
}) => {
    try {
        let complete_url = `${process.env.NEXT_PUBLIC_SERVER}/${url}`;
        
        if(user_id)
            complete_url += `/${user_id}`;

        if(other_id)
            complete_url += `/${other_id}`;

        const response = await fetch(complete_url).catch((err) => {
            throw {...err.cause, status: 500}
        });

        const status = response.status;
        const data = await response.json();

        if(status == 200)
            return data;
        else
            throw {...data, status: status};

    } catch (err) {  
        return err;
    }
}