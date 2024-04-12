import Grid from '@mui/material/Grid';

import ListComponent from '@/components/general/ListComponent';

import AddCircleIcon from '@mui/icons-material/AddCircle'

import ListRequestBody from '@/components/general/ListRequestBody';
import ListTableMasterBody from '@/components/master/ListTableMasterBody';
import CreateModalTable from '@/components/tables/modals/CreateModalTable';

import { Error, ErrorMessage } from '@/components/info/HandlerError';
import { getter } from '@/api/getter';
import { useModal } from '@/hooks/useModal';

export const getServerSideProps = async () => {
    const result = await getter("1", 'tables/master');

    if(!result.status) {
        return {
            props: {
                owner_tables: result.owner_tables,
                master_tables: result.master_tables,
                request_tables: result.request_tables,
                err: false
            }
        }
    }
    else
        return {
            props: {
                err: result
            }
        }
}

export default function Masters (props) {
    const { owner_tables, master_tables, request_tables, err } = props;
    const { open, handleOpenModal, handleCloseModal} = useModal();

    return (
        <Grid
            container
            spacing={2}
        >
            <Error>
                <Error.When isError={err ? true : false}>
                    <ListComponent
                        xs={12}
                        lg={8}
                        title="Mis mesas"
                        description="Ultimas mesas que haz creadas y se encuentran activas."
                        action={{Icon: AddCircleIcon, handleClickButton: handleOpenModal}}
                    >
                        <ListTableMasterBody
                            tables={owner_tables}
                        />
                    </ListComponent>
                    <ListComponent
                        xs={12}
                        lg={4}
                        title="Solicitudes"
                        description="Ultimas solicitudes recibidas y sin revisar."
                    >
                        <ListRequestBody
                            requests={request_tables}
                            isMaster={true}
                         />
                    </ListComponent>
                    {open &&
                        <CreateModalTable
                            isOpen={open}
                            handleCloseModal={handleCloseModal}
                        />
                    }
                </Error.When>
                <Error.Else>
                    <ErrorMessage err={err}/>
                </Error.Else>
            </Error>    
        </Grid>
    );
}