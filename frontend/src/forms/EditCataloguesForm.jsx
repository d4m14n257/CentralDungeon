import { useCallback, useContext, useState } from "react";
import { useForm, Controller } from "react-hook-form";
import { useDebouncedCallback } from "use-debounce";
import { zodResolver } from "@hookform/resolvers/zod";

import { createFilterOptions } from '@mui/material/Autocomplete';
import { Autocomplete, TextField, FormControl, FormHelperText, Stack, Divider } from "@mui/material";
import LoadingButton from '@mui/lab/LoadingButton';

import SendIcon from '@mui/icons-material/Send';

import { z } from "zod";
import { modal } from "@/styles/tables/modal";
import { getter } from "@/api/getter";
import { Confirm } from "@/contexts/ConfirmContext";
import { Message } from "@/contexts/MessageContext";
import { putter } from "@/api/putter";

const filter = createFilterOptions();

const schema = z.object ({
    catalogues: z.object({
        id: z.string(),
        name: z.string(),
        status: z.string().nullable().default('Saved')
    }).array(),
})  

export default function EditCataloguesForm (props) {
    const { handleCloseModal, reloadAction, data, type, table_id } = props;
    const [listCatalogue, setListCatalogue] = useState([]);
    const { confirm, setMessage } = useContext(Confirm);
    const { handleOpen, setMessage : setStatusMessage, setStatus, setInfo } = useContext(Message);

    const { control, register, handleSubmit, formState: { isSubmitting }} = useForm({
        defaultValues: {
            catalogues: data
        },
        resolver: zodResolver(schema)
    })

    const handleCataloguesList = useDebouncedCallback(async (event, value) => {
        let url = '';

        switch (type) {
            case 'Tags': {
                url = 'tags'
                break;
            }
            case 'Sistemas': {
                url = 'systems';
                break;
            }
            case 'Plataformas': {
                url = 'platforms';
                break;
            }
        }

        if(value.length > 0) {
            const data = await getter({others: value, url: url});

            if(data.status) {
                setListCatalogue([]);
                return;
            }

            setListCatalogue(data);
        }
    }, 300)

    const onSubmit = useCallback(async (value, event) => {
        try {
            let url;
            let successfully;
            let error;

            if(value.catalogues.length == data.length) { 
                let count = 0;

                value.catalogues.forEach(element => {
                    data.forEach(item => {
                        if(item.id == element.id && item.name == element.name)
                            count++;
                    })
                });


                if(data.length == count) {
                    setInfo(true);
                    setStatusMessage('No ha habido cambios en los datos.');
                    handleOpen();

                    throw {message: 'No hay cambios'}
                }
            }

            setMessage('¿Esta seguro de crear esta mesa?');

            if(!event.shiftKey) {
                await confirm()
                    .catch(() => {throw {canceled: true}});
            }

            switch (type) {
                case 'Tags': {
                    url = 'tags'
                    successfully = 'Tags de la mesa editados con exito.'
                    error = 'Ha habido un error al momento de editar los tags de la mesa.'
                    break;
                }
                case 'Sistemas': {
                    url = 'systems';
                    successfully = 'Sistemas de la mesa editados con exito.'
                    error = 'Ha habido un error al momento de editar los sistemas de la mesa.'
                    break;
                }
                case 'Plataformas': {
                    url = 'platforms';
                    successfully = 'Plataformas de la mesa editadas con exito.'
                    error = 'Ha habido un error al momento de editar las plataformas de la mesa.'
                    break;
                }
            }

            url += '/tables'

            const response = await putter(
            {   
                id: table_id,
                data: {
                    original: data,
                    change: value.catalogues
                },
                url: url
            });

            if(response.status >= 200 && response.status <= 299) {
                setStatus(response.status);
                setStatusMessage('Datos editados con exito.');
                handleOpen();
                handleCloseModal();
                
                if(reloadAction) {
                    await reloadAction();
                }
            }
            else
                throw {message: 'Ha habido un error al editar la mesa.', status: response.status}
        }
        catch (err) {
            if(!err.canceled) {
                setStatus(err.status);
                setMessage(err.message);
                handleOpen();
            }
        }
    }, [])

    return (
        <form style={{...modal.content, width: '480px'}}>
            <Divider />
            <Controller 
                control={control}
                render={({ field }) => 
                    <FormControl>
                        <Autocomplete
                            fullWidth
                            multiple
                            limitTags={8}
                            disabled={isSubmitting}
                            options={listCatalogue}
                            filterSelectedOptions
                            isOptionEqualToValue={(option, value) => option.id === value.id}
                            getOptionLabel={(option) => typeof option === 'string' ? option : option.name}
                            onInputChange={(event, newValue) => {handleCataloguesList(event, newValue)}}
                            renderInput={(params) => (<TextField {...params} label="Edicion de lista" />)}
                            value={field.value}
                            onChange={(e, value, reason) => field.onChange(value)}
                            filterOptions={(options, params) => {
                                const filtered = filter(options, params)
                                const { inputValue } = params;
                            
                                const isExisting = options.some((option) => inputValue === option.name);
                                if (inputValue !== '' && !isExisting) {

                                    filtered.push({
                                        status: inputValue,
                                        name: `Agregar "${inputValue}"`,
                                        id: `${inputValue}`
                                    });
                                }

                                return filtered;
                            }}
                        />
                        <FormHelperText>Puede editar los datos que sean nesesarios.</FormHelperText>
                    </FormControl>
                }
                {...register("catalogues")}
            />
            <Stack direction='row' justifyContent='end'>
                <LoadingButton 
                    loading={isSubmitting}
                    variant='outlined' 
                    type='submit' 
                    onClick={(event) => handleSubmit(onSubmit)(event)}
                    endIcon={<SendIcon />}
                >
                    Subir
                </LoadingButton>
            </Stack>
        </form>
    );
}