import Grid from "@mui/material/Grid";

import ListComponent from "@/components/general/ListComponent";
import ListRequestBody from "@/components/general/ListRequestBody";

import { Request } from "@/normalize/models";
import { getter } from "@/api/getter";
import { Error, ErrorMessage } from "@/components/info/HandlerError";

export const getServerSideProps = async () => {
    const result = await getter({user_id: "2", url: 'users/requests/player'});

    if(!result.status) {
        const data = {
            request_tables_candidate: Request(result.request_tables_candidate),
            request_tables_rejected: Request(result.request_tables_rejected)
        }

        return {
            props: {
                ...data,
                err: false
            }
        }
    }
    else {
        return {
            props: {
                err: result
            }
        }
    }
}

export default function PlayerRequest (props) {
    const { request_tables_candidate, request_tables_rejected, err } = props;

    return (
        <Grid
            container
            spacing={2}
        >
            <Error>
                <Error.When isError={err}>
                    <ListComponent
                        lg={6}
                        title='Peticiones realizadas'
                        description='Todas las peticiones realizadas hasta el momento.'
                        hasCollapse
                    >
                        <ListRequestBody 
                            requests={request_tables_candidate}
                        />
                    </ListComponent>
                    <ListComponent
                        lg={6}
                        title='Peticiones rehazadas'
                        description='Todas que no fueron aceptadas hasta el momento.'
                        hasCollapse
                    >
                        <ListRequestBody 
                            requests={request_tables_rejected}
                        />
                    </ListComponent>
                </Error.When>
                <Error.Else>
                    <ErrorMessage err={err} />
                </Error.Else>
            </Error>
        </Grid>
    );
}