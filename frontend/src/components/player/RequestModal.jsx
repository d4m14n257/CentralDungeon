import { useCallback, useEffect, useState } from "react";
import { Box, Button, Divider, Skeleton, Typography } from "@mui/material";

import ModalBase from "../general/ModalBase";
import { modal } from "@/styles/tables/modal";
import { getter } from "@/api/getter";
import { Error, ErrorMessage } from "../info/HandlerError";
import { Rejected } from "@/normalize/models";
import Span from "../Span";
import { useDate } from "@/hooks/useDate";

const useModalRequest = ({ request, isOpen }) => {
    const [ data, setData ] = useState(null);
    const { handleDatetime } = useDate();
   
    const handleRequest = useCallback(async (request) => {
        const result = await getter({id: '2', others: request.id, url: 'users/request/rejected'})

        console.log(result)

        return result;
    }, [isOpen])

    useEffect(() => {
        handleRequest(request).then((response) => {
            if(response.status == 200) {
                const rejected = Rejected(response)
                
                rejected.rejected_date = handleDatetime(rejected.rejected_date)
                
                setData({...rejected, ...request, created: handleDatetime(request.created), err: false});
            }
            else {
                setData({err: response});
            }

        });

    }, [isOpen])

    return {
        data
    }
}

export default function RequestModal (props) {
    const { isOpen, handleCloseModal, request } = props;
    const { data } = useModalRequest({ request: request.current, isOpen })

    return (
        <ModalBase
            isOpen={isOpen}
            handleCloseModal={handleCloseModal}
        >
            <Box sx={modal.body}>
                <Box sx={modal.header}>
                    <Typography variant='h5'>
                        Estado de la solicitud.
                    </Typography>
                    <Divider />
                </Box>
                    {data ? 
                        <Error>
                            <Error.When isError={data.err}>
                                <Box sx={modal.content}>
                                    <Typography variant='body2'><Span title='Nombre de la mesa: '/>{data.name}</Typography>
                                    <Typography variant='body2'><Span title='Masters: '/>{data.masters}</Typography>
                                    <Typography variant='body2'><Span title='Estado: '/>{data.status}</Typography>
                                    <Typography variant='body2'><Span title='Motivo: '/>{data.description_rejected}</Typography>
                                    <Typography variant='body2'><Span title='Fecha de solicitud: '/>{data.created}</Typography>
                                    <Typography variant='body2'><Span title='Fecha de revisión: '/>{data.rejected_date}</Typography>
                                    <Divider />
                                </Box>
                                <Box sx={modal.footer}>
                                    <Button variant="outlined">
                                        Ir a mesa
                                    </Button>
                                    <Button variant="contained">
                                        Nueva solicitud
                                    </Button>
                                 </Box>
                            </Error.When>
                            <Error.Else>
                                <ErrorMessage err={data}/>
                            </Error.Else>
                        </Error> :
                        <Skeleton variant="rounded" width={280} height={100}/>
                    }
                </Box>
        </ModalBase>
    );
}