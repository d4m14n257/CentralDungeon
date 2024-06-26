import Grid from '@mui/material/Grid';

import ListComponent from '@/components/general/ListComponent';

import ListRequestBody from '@/components/general/ListRequestBody';
import ListTableMasterBody from '@/components/master/ListTableMasterBody';
import CreateModalTable from '@/components/tables/modals/CreateModalTable';

import { Error, ErrorMessage } from '@/components/info/HandlerError';
import { getter } from '@/api/getter';
import { useModal } from '@/hooks/useModal';
import { Request, Tables } from '@/normalize/models';
import { useRouter } from 'next/router';
import { MASTER_REQUEST, TABLES } from '@/constants/constants';

import { MessageContext } from '@/contexts/MessageContext';
import { ShiftContext } from '@/contexts/ShiftContext';
import ActionMasterList from '@/components/master/ActionMasterList';

export const getServerSideProps = async () => {
    const result = await getter({id: "1", url: 'tables/master'});

    if(result.status == 200) {
        const data = {
            owner_tables: Tables(result.owner_tables),
            master_tables: Tables(result.master_tables),
            request_tables: Request(result.request_tables)
        }

        return {
            props: {
                ...data,
                err: false
            }
        }
    }
    else
        return {
            props: {
                err_result: result,
                err: true
            }
        }
}

export default function Masters (props) {
    const { owner_tables, master_tables, request_tables, err, err_result } = props;
    const { open, handleOpenModal, handleCloseModal} = useModal();
    const router = useRouter();

    const handleTableSelect = () => {
        router.push(TABLES);
    }

    const handleRequestSelect = () => {
        router.push(MASTER_REQUEST);
    }

    return (
        <Grid
            container
            spacing={2}
        >
            <Error>
                <Error.When isError={err}>
                    <ListComponent
                        xs={12}
                        lg={8}
                        title="Mis mesas"
                        description="Ultimas mesas que haz creadas y se encuentran activas."
                        Action={ActionMasterList}
                        handleAction={handleOpenModal}
                        handleExpand={handleTableSelect}
                        hasCollapse
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
                        handleExpand={handleRequestSelect}
                        hasCollapse
                    >
                        <ListRequestBody
                            requests={request_tables}
                            isMaster={true}
                         />
                    </ListComponent>
                    <MessageContext>
                        {open &&
                            <ShiftContext>
                                <CreateModalTable
                                    isOpen={open}
                                    handleCloseModal={handleCloseModal}
                                    closeConfirm
                                />
                            </ShiftContext>
                        }
                    </MessageContext>
                </Error.When>
                <Error.Else>
                    <ErrorMessage {...err_result}/>
                </Error.Else>
            </Error>    
        </Grid>
    );
}