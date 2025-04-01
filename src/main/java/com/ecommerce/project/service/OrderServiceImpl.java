package com.ecommerce.project.service;

import com.ecommerce.project.exceptions.APIException;
import com.ecommerce.project.exceptions.ResourceNotFoundException;
import com.ecommerce.project.model.*;
import com.ecommerce.project.payload.OrderDTO;
import com.ecommerce.project.payload.OrderItemDTO;
import com.ecommerce.project.payload.PaymentDTO;
import com.ecommerce.project.payload.ProductDTO;
import com.ecommerce.project.repositories.*;
import jakarta.transaction.Transactional;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {
    @Autowired
    private CartRepository cartRepository;
    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private OrderItemRepository orderItemRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private CartService cartService;
    @Autowired
    private ModelMapper modelMapper;
    @Autowired
    private PaymentRepository paymentRepository;

    @Transactional
    @Override
    public OrderDTO placeOrder(String emailId, Long addressId, String paymentMethod, String pgName, String pgPaymentId, String pgStatus, String pgResponseMessage) {
        Cart cart = cartRepository.findCartByEmail(emailId);
        if (cart == null) {
            throw new ResourceNotFoundException("Cart", "email", emailId);
        }

        // create order
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address", "addressId", addressId));
        Order order = new Order();
        order.setEmail(emailId);
        order.setOrderDate(LocalDate.now());
        order.setTotalAmount(cart.getTotalPrice());
        order.setOrderStatus("Order Accepted!");
        order.setAddress(address);
        Payment payment = new Payment(paymentMethod, pgName, pgPaymentId, pgStatus, pgResponseMessage);
        paymentRepository.save(payment);
        order.setPayment(payment);
        Order savedOrder = orderRepository.save(order);


        List<CartItem> cartItems = cart.getCartItems();
        if(cartItems.isEmpty()) {
            throw new APIException("Cart is empty");
        }
        // create orderItems
        List<OrderItem> orderItems = new ArrayList<>();
        for (CartItem cartItem : cartItems) {
            OrderItem orderItem = new OrderItem();
            orderItem.setProduct(cartItem.getProduct());
            orderItem.setOrder(order);
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setDiscount(cartItem.getDiscount());
            orderItem.setOrderedProductPrice(cartItem.getProductPrice());
            orderItems.add(orderItem);
        }
        orderItemRepository.saveAll(orderItems);



        // update cart stock and remove Item from cart
        cart.getCartItems().forEach(cartItem -> {
            int quantity = cartItem.getQuantity();

            // update product quantity
            Product product = cartItem.getProduct();
            product.setQuantity(product.getQuantity() - quantity);

            // delete product from cart
            cartService.deleteProductFromCart(cart.getCartId(), product.getProductId());
        });

        // return orderDTO
        OrderDTO orderDTO = modelMapper.map(savedOrder, OrderDTO.class);
        List<OrderItemDTO> orderItemDTOS = orderItems.stream()
                .map(orderItem -> (modelMapper.map(orderItem, OrderItemDTO.class)))
                .toList();
        orderDTO.setOrderItems(orderItemDTOS);
        orderDTO.setPaymentDTO(modelMapper.map(payment, PaymentDTO.class));
        orderDTO.setAddressId(addressId);
        return orderDTO;
    }
}
